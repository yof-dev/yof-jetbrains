package io.yof.jetbrains

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
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

    fun checkNow() {
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
                    ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart() }
                } catch (e: CheckOutputException) {
                    append("[check] ${e.message}")
                }
            }

            override fun onFinished() { running = null }
        })
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
