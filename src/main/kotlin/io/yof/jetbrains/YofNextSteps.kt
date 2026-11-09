package io.yof.jetbrains

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

/**
 * What to do after a check (0.1.1, 2026-11-07), the same as the VS Code extension: Copy fix prompt, Get a signed report,
 * Turn on Patch. The last two only open a page on app.yof.io in the browser: no network call from the plugin, no
 * telemetry. Until ordering opens they say "(opens soon)", the words app.yof.io shows on its order buttons;
 * ORDERING_OPEN is flipped in the release after launch, the plugin never asks a server.
 */
object YofNextSteps {
    const val ORDERING_OPEN = false
    const val SIGNED_REPORT_URL = "https://app.yof.io/yof/orders"
    const val PATCH_URL = "https://app.yof.io/yof/patch"

    fun label(base: String, open: Boolean = ORDERING_OPEN): String = if (open) base else "$base (opens soon)"
    fun signedReportLabel(open: Boolean = ORDERING_OPEN) = label("Get a signed report", open)
    fun patchLabel(open: Boolean = ORDERING_OPEN) = label("Turn on Patch", open)

    /** Every open finding's fix prompt, numbered, to paste into an AI assistant in one go (masked values only). */
    fun combinedPrompt(prompts: List<String>): String {
        val list = prompts.map { it.trim() }.filter { it.isNotEmpty() }
        if (list.size <= 1) return list.firstOrNull().orEmpty()
        return (listOf("yof found ${list.size} problems. Fix them one by one:") + list.mapIndexed { i, p -> "${i + 1}. $p" }).joinToString("\n\n")
    }

    fun copy(text: String) = CopyPasteManager.getInstance().setContents(StringSelection(text))
}

class YofSignedReportAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = BrowserUtil.browse(YofNextSteps.SIGNED_REPORT_URL)
}

class YofPatchAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) = BrowserUtil.browse(YofNextSteps.PATCH_URL)
}
