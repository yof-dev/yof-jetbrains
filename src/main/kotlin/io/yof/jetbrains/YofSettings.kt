package io.yof.jetbrains

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * The plugin's settings, kept per IDE (not in the project), so a project can never point the plugin at a program of its
 * own. Settings > Tools > yof.
 */
@Service(Service.Level.APP)
@State(name = "YofSettings", storages = [Storage("yof.xml")])
class YofSettings : SimplePersistentStateComponent<YofSettings.Options>(Options()) {
    class Options : BaseState() {
        /** Run `yof check` when a file is saved. */
        var checkOnSave by property(true)
        /** Package-registry lookups (npm, PyPI, OSV). Off: the check makes no network call at all. */
        var packageLookups by property(false)
        /** Empty: the profile in .yof.yml. */
        var profile by string("")
        /** The Node.js that runs the bundled CLI. */
        var nodePath by string("node")
        /** A yof executable to run instead of the bundled CLI. */
        var cliPath by string("")
        var checkTimeoutSeconds by property(60)
        var attackTarget by string("http://localhost:3000")
        var attackConfig by string(".yof/attack.json")
        /** full, or light (read-only). */
        var attackMode by string("full")
    }

    companion object {
        fun get(): YofSettings = service()
    }
}

/** The CLI arguments, from the settings. Same as the VS Code extension. */
object YofArgs {
    fun check(o: YofSettings.Options): List<String> {
        val args = mutableListOf("check", ".", "--json")
        if (!o.packageLookups) args += "--offline"
        val profile = o.profile.orEmpty()
        if (profile in setOf("relaxed", "balanced", "strict")) args += listOf("--profile", profile)
        return args
    }

    fun attack(o: YofSettings.Options): List<String> = listOf(
        "attack", o.attackTarget.orEmpty().ifBlank { "http://localhost:3000" },
        "--config", o.attackConfig.orEmpty().ifBlank { ".yof/attack.json" },
        "--mode", if (o.attackMode == "light") "light" else "full",
        "--json",
    )
}
