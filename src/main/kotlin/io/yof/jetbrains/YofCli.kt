package io.yof.jetbrains

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProgressIndicator
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Runs the yof CLI as a child process, always from a background task: never on the UI thread, with a time limit, and
 * the task's cancel button kills it. Either the yof executable from the settings, or the CLI bundled with this plugin
 * (yof/yof-cli.cjs) run by Node.js. Nothing is downloaded.
 */
object YofCli {
    private const val RESOURCE = "/yof/yof-cli.cjs"

    /** The bundled CLI, copied once per plugin version to the IDE's temp folder (a resource can't be run in place). */
    private fun bundledScript(): File {
        val version = YofCli::class.java.`package`?.implementationVersion ?: "dev"
        val target = File(PathManager.getTempPath(), "yof-cli-$version.cjs")
        if (!target.exists()) {
            val input = YofCli::class.java.getResourceAsStream(RESOURCE)
                ?: throw IllegalStateException("The bundled yof CLI is missing from this plugin build.")
            input.use { Files.copy(it, target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
        return target
    }

    fun commandLine(args: List<String>, workDir: String, extraEnv: Map<String, String> = emptyMap()): GeneralCommandLine {
        val o = YofSettings.get().state
        val cliPath = o.cliPath.orEmpty().trim()
        val cmd = if (cliPath.isNotEmpty()) {
            GeneralCommandLine(listOf(cliPath) + args)
        } else {
            GeneralCommandLine(listOf(o.nodePath.orEmpty().ifBlank { "node" }, bundledScript().absolutePath) + args)
        }
        return cmd.withWorkDirectory(workDir)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment(extraEnv + mapOf("NO_COLOR" to "1"))
            .withCharset(Charsets.UTF_8)
    }

    /** Runs to the end, the time limit or the indicator's cancel, whichever comes first. */
    fun run(args: List<String>, workDir: String, timeoutMs: Int, indicator: ProgressIndicator, extraEnv: Map<String, String> = emptyMap()): ProcessOutput =
        CapturingProcessHandler(commandLine(args, workDir, extraEnv)).runProcessWithProgressIndicator(indicator, timeoutMs)
}
