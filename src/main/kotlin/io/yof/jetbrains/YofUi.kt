package io.yof.jetbrains

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

/** The yof tool window: what each check and attack run said, newest at the bottom. */
class YofToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val svc = project.service<YofProjectService>()
        val area = JBTextArea(svc.logText()).apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
        svc.onLog { line -> ApplicationManager.getApplication().invokeLater { area.append(line + "\n") } }
        val content = ContentFactory.getInstance().createContent(JBScrollPane(area), "", false)
        toolWindow.contentManager.addContent(content)
    }
}

/** Settings > Tools > yof. */
class YofConfigurable : BoundConfigurable("yof") {
    override fun createPanel(): DialogPanel {
        val o = YofSettings.get().state
        return panel {
            group("Check") {
                row { checkBox("Check the project when a file is saved").bindSelected(o::checkOnSave) }
                row { checkBox("Also check dependencies against npm, PyPI and OSV (off: no network call at all)").bindSelected(o::packageLookups) }
                row("Profile:") {
                    comboBox(listOf("", "relaxed", "balanced", "strict")).bindItem({ o.profile.orEmpty() }, { o.profile = it.orEmpty() })
                        .comment("Empty: the profile in .yof.yml")
                }
                row("Stop a check after (seconds):") { intTextField(5..600).bindIntText(o::checkTimeoutSeconds) }
                row("Node.js:") { textField().bindText({ o.nodePath.orEmpty() }, { o.nodePath = it }).comment("Runs the yof CLI bundled with this plugin") }
                row("yof executable:") { textField().bindText({ o.cliPath.orEmpty() }, { o.cliPath = it }).comment("Optional: run this yof instead of the bundled one") }
            }
            group("yof attack") {
                row("Address:") { textField().bindText({ o.attackTarget.orEmpty() }, { o.attackTarget = it }).comment("localhost, or a host you proved is yours") }
                row("Config:") { textField().bindText({ o.attackConfig.orEmpty() }, { o.attackConfig = it }) }
                row("Mode:") { comboBox(listOf("full", "light")).bindItem({ o.attackMode ?: "full" }, { o.attackMode = it ?: "full" }) }
            }
        }
    }
}
