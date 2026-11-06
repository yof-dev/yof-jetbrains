package io.yof.jetbrains

import com.google.gson.JsonParser
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import java.io.File

/** A save schedules a check for the file's project (in the background, after a short pause). */
class YofSaveListener : FileDocumentManagerListener {
    override fun beforeDocumentSaving(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return
        project.service<YofProjectService>().scheduleCheck()
    }
}

class YofCheckAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        e.project?.service<YofProjectService>()?.checkNow()
    }
}

/**
 * yof attack: checks the config and the consent sentence, asks with a notification (not a dialog, so typing goes on),
 * then runs the CLI in a cancellable background task. The CLI enforces every safety rule again.
 */
class YofAttackAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val base = project.basePath ?: return
        val o = YofSettings.get().state
        val configPath = o.attackConfig.orEmpty().ifBlank { ".yof/attack.json" }
        val target = o.attackTarget.orEmpty().ifBlank { "http://localhost:3000" }
        val svc = project.service<YofProjectService>()
        val problem = YofAttack.preflight(File(base, configPath).takeIf { it.isFile }?.readText(), configPath)
        if (problem != null) { svc.notify(problem, NotificationType.WARNING); return }
        NotificationGroupManager.getInstance().getNotificationGroup("yof")
            .createNotification(YofAttack.confirmText(target, configPath, o.attackMode == "light"), NotificationType.INFORMATION)
            .addAction(NotificationAction.createSimpleExpiring("Start") { run(project, base, svc) })
            .addAction(NotificationAction.createSimpleExpiring("Not now") {})
            .notify(project)
    }

    private fun run(project: Project, base: String, svc: YofProjectService) {
        val o = YofSettings.get().state
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "yof attack", true) {
            override fun run(indicator: ProgressIndicator) {
                svc.append("[attack] ${o.attackTarget} (${o.attackMode} run, ${o.attackConfig})")
                val out = YofCli.run(YofArgs.attack(o), base, 16 * 60 * 1000, indicator)
                if (indicator.isCanceled || out.isCancelled || out.isTimeout) {
                    svc.append("[attack] stopped. The CLI deletes the test records it created when it is stopped; if the IDE had to end it before that, they may still be there (they hold the run's yof marker text).")
                    return
                }
                if (out.exitCode != 0 && out.exitCode != 1) {
                    svc.append("[attack] could not run (code ${out.exitCode}): ${out.stderr.trim()}")
                    svc.notify("yof attack could not run: ${out.stderr.trim().lineSequence().firstOrNull().orEmpty()}", NotificationType.WARNING)
                    return
                }
                val headline = YofAttack.summarize(out.stdout).onEach { svc.append(it) }.lastOrNull() ?: "yof attack finished."
                svc.notify(headline)
            }
        })
    }
}

/** The attack checks and words, the same as the VS Code extension's. */
object YofAttack {
    fun consentText(app: String) = "I own $app and allow yof attack to test it and its preview builds with my own test accounts."

    /** null when the config is ready, otherwise what is missing. */
    fun preflight(text: String?, path: String): String? {
        if (text == null) return "There is no $path yet. yof attack needs one: it names your app, your written consent and the test accounts (as environment variable names, never passwords). The yof README has an example."
        val obj = try { JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject } catch (e: Exception) { null }
            ?: return "$path is not a valid JSON object."
        val app = obj.get("app")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.lowercase()
        if (app.isNullOrEmpty()) return "$path must name your app (\"app\")."
        val consent = obj.get("consent")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
        if (consent != consentText(app)) return "$path must carry your consent, word for word: ${consentText(app)}"
        return null
    }

    fun confirmText(target: String, path: String, light: Boolean): String {
        val what = if (light) "read-only requests" else "requests, including test records it creates and deletes again"
        return "yof attack will send $what to $target, signed in as the test accounts named in $path. Start it?"
    }

    /** The report in plain lines; the last line is the headline. */
    fun summarize(json: String): List<String> {
        val r = try { JsonParser.parseString(json).asJsonObject } catch (e: Exception) { return listOf("yof attack did not print JSON.") }
        val lines = mutableListOf<String>()
        val findings = r.getAsJsonArray("findings")?.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }.orEmpty()
        for (f in findings) {
            lines += "  ${f.get("title")?.asString ?: "Finding"} (${f.get("severity")?.asString?.replace("_", " ") ?: "?"})"
            lines += "    ${f.get("method")?.asString.orEmpty()} ${f.get("route")?.asString.orEmpty()}: ${f.get("evidence")?.asString.orEmpty()}"
        }
        val now = findings.count { it.get("severity")?.asString == "fix_now" }
        lines += if (findings.isEmpty()) "yof attack found nothing in what it tested. A run shows what it tried, not that nothing can go wrong."
        else "yof attack found ${findings.size} problem${if (findings.size == 1) "" else "s"}${if (now > 0) ", $now to fix now" else ""}."
        return lines
    }
}
