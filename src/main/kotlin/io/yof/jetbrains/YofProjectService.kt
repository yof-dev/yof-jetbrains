package io.yof.jetbrains

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * One per project: runs `yof check` in the background (a save schedules it, a newer save replaces a pending one),
 * keeps the latest findings per file for the annotator, and writes to the yof tool window.
 */
@Service(Service.Level.PROJECT)
class YofProjectService(private val project: Project) : Disposable {
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    @Volatile private var findings: Map<String, List<Finding>> = emptyMap()
    @Volatile private var running: ProgressIndicator? = null
    private val log = StringBuilder()
    private val listeners = ConcurrentHashMap.newKeySet<(String) -> Unit>()

    fun findingsFor(relativePath: String): List<Finding> = findings[relativePath].orEmpty()

    fun basePath(): String? = project.basePath

    /** Called on save: waits a moment so a burst of saves runs one check. */
    fun scheduleCheck(delayMs: Int = 400) {
        if (!YofSettings.get().state.checkOnSave) return
        alarm.cancelAllRequests()
        alarm.addRequest({ ApplicationManager.getApplication().invokeLater { checkNow() } }, delayMs)
    }

    /** `manual`: the person ran Check This Project; when it finishes, the next steps are offered (YofNextSteps). */
    fun checkNow(manual: Boolean = false) {
        val base = project.basePath ?: return
        running?.cancel()   // a newer check replaces the older one
        val o = YofSettings.get().state
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "yof check", true) {
            override fun run(indicator: ProgressIndicator) {
                running = indicator
                val out = YofCli.run(YofArgs.check(o), base, o.checkTimeoutSeconds.coerceIn(5, 600) * 1000, indicator)
                if (indicator.isCanceled || out.isCancelled) return
                if (out.isTimeout) { append("[check] stopped after ${o.checkTimeoutSeconds} s."); return }
                if (out.exitCode != 0 && out.exitCode != 1) { append("[check] yof could not run (code ${out.exitCode}): ${out.stderr.trim()}"); return }
                try {
                    val list = YofReport.parse(out.stdout) { rel -> File(base, rel).takeIf { it.isFile }?.readText() }
                    findings = list.groupBy { it.file }
                    val now = list.count { it.level == Level.ERROR }
                    val week = list.count { it.level == Level.WARNING }
                    append(if (now + week == 0) "[check] nothing to fix." else "[check] $now to fix now, $week to fix this week.")
                    if (manual) offerNextSteps(list.filter { it.level != Level.INFORMATION })
                    ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart() }
                } catch (e: CheckOutputException) {
                    append("[check] ${e.message}")
                }
            }

            override fun onFinished() { running = null }
        })
    }

    private fun offerNextSteps(open: List<Finding>) {
        val text = if (open.isEmpty()) "yof: nothing to fix in this project. A signed report covers your live app too."
        else "yof: ${open.size} to fix. Paste the fix prompt into your AI assistant, or get a signed report for your app."
        val n = NotificationGroupManager.getInstance().getNotificationGroup("yof").createNotification(text, NotificationType.INFORMATION)
        val prompt = YofNextSteps.combinedPrompt(open.map { it.fixPrompt })
        if (prompt.isNotEmpty()) n.addAction(NotificationAction.createSimple("Copy fix prompt") { YofNextSteps.copy(prompt) })
        n.addAction(NotificationAction.createSimple(YofNextSteps.signedReportLabel()) { BrowserUtil.browse(YofNextSteps.SIGNED_REPORT_URL) })
        n.addAction(NotificationAction.createSimple(YofNextSteps.patchLabel()) { BrowserUtil.browse(YofNextSteps.PATCH_URL) })
        n.notify(project)
    }

    fun append(line: String) {
        synchronized(log) { log.append(line).append('\n') }
        for (l in listeners) l(line)
    }

    fun logText(): String = synchronized(log) { log.toString() }
    fun onLog(listener: (String) -> Unit) { listeners += listener }

    fun notify(text: String, type: NotificationType = NotificationType.INFORMATION) {
        NotificationGroupManager.getInstance().getNotificationGroup("yof").createNotification(text, type).notify(project)
    }

    override fun dispose() { running?.cancel() }
}
