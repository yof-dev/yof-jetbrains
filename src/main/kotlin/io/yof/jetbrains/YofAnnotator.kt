package io.yof.jetbrains

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.awt.datatransfer.StringSelection

/**
 * Shows the latest `yof check` findings for a file. The annotator itself runs nothing: the check runs in the background
 * on save (YofProjectService), and this only reads its stored results, so it adds nothing to typing.
 */
class YofAnnotator : ExternalAnnotator<YofAnnotator.Info, List<Finding>>() {
    data class Info(val findings: List<Finding>)

    override fun collectInformation(file: PsiFile): Info? {
        val project = file.project
        val base = project.basePath ?: return null
        val vFile = file.virtualFile ?: return null
        val rel = FileUtil.getRelativePath(base, vFile.path, '/') ?: return null
        if (rel.startsWith("../")) return null
        val list = project.service<YofProjectService>().findingsFor(rel)
        return if (list.isEmpty()) null else Info(list)
    }

    override fun doAnnotate(info: Info?): List<Finding> = info?.findings.orEmpty()

    override fun apply(file: PsiFile, result: List<Finding>, holder: AnnotationHolder) {
        val doc = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return
        for (f in result) {
            if (f.line >= doc.lineCount) continue
            val lineStart = doc.getLineStartOffset(f.line)
            val lineEnd = doc.getLineEndOffset(f.line)
            val start = (lineStart + f.startColumn).coerceIn(lineStart, lineEnd)
            val end = (lineStart + f.endColumn).coerceIn(start, lineEnd)
            val severity = when (f.level) {
                Level.ERROR -> HighlightSeverity.ERROR
                Level.WARNING -> HighlightSeverity.WARNING
                Level.INFORMATION -> HighlightSeverity.WEAK_WARNING
            }
            var builder = holder.newAnnotation(severity, "yof: ${f.message}").range(TextRange(start, maxOf(end, start)))
            if (f.fixPrompt.isNotEmpty()) builder = builder.withFix(CopyFixPrompt(f.fixPrompt))
            builder.create()
        }
    }
}

/** "Copy the fix prompt for your AI assistant": the CLI's prompt, with the masked value only. */
class CopyFixPrompt(private val prompt: String) : IntentionAction {
    override fun getText() = "yof: Copy the fix prompt for your AI assistant"
    override fun getFamilyName() = "yof"
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true
    override fun startInWriteAction() = false
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        CopyPasteManager.getInstance().setContents(StringSelection(prompt))
    }
}
