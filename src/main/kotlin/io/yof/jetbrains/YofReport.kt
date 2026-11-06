package io.yof.jetbrains

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * `yof check --json` in, findings out. No IDE classes here, so the mapping is unit-tested on its own. The words come
 * from the CLI as they are (`why`, `fixPrompt`); a value is only ever the masked one the CLI prints.
 */
enum class Level { ERROR, WARNING, INFORMATION }

data class Finding(
    /** Relative to the checked folder, forward slashes. */
    val file: String,
    /** 0-based. */
    val line: Int,
    val startColumn: Int,
    val endColumn: Int,
    val level: Level,
    val code: String,
    val message: String,
    val fixPrompt: String,
)

class CheckOutputException(message: String) : Exception(message)

object YofReport {
    private val LABEL = mapOf("fix_now" to "fix now", "fix_week" to "fix this week", "info" to "fine, public by design")
    private val LEVEL = mapOf("fix_now" to Level.ERROR, "fix_week" to Level.WARNING, "info" to Level.INFORMATION)

    /** Which findings a profile shows: the same rule as the CLI's text output. */
    fun visibleIn(profile: String, severity: String): Boolean = when (profile) {
        "strict" -> true
        "balanced" -> severity != "info"
        else -> severity == "fix_now"
    }

    fun parse(text: String, readText: (String) -> String? = { null }): List<Finding> {
        val root: JsonObject = try {
            JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            null
        } ?: throw CheckOutputException("yof did not print JSON. Is the yof CLI 0.2.0 or newer?")
        if (root.str("tool") != "yof" || !root.has("keys") || !root.has("packages")) {
            throw CheckOutputException("yof printed JSON in a shape this plugin does not know.")
        }
        val profile = root.str("profile") ?: "balanced"
        val lines = HashMap<String, List<String>?>()
        fun linesOf(file: String) = lines.getOrPut(file) { readText(file)?.split(Regex("\\r?\\n")) }

        val out = ArrayList<Finding>()
        for (el in root.getAsJsonArray("keys")) {
            val k = el.obj() ?: throw CheckOutputException("yof printed a key finding this plugin does not know.")
            val severity = k.str("severity") ?: continue
            if (!visibleIn(profile, severity)) continue
            val file = norm(k.str("file") ?: continue)
            val line = maxOf(0, (k.int("line") ?: 1) - 1)
            val column = k.int("column") ?: 1
            val masked = k.str("masked") ?: ""
            val (start, end) = keySpan(linesOf(file)?.getOrNull(line), column, masked)
            val why = k.str("why")?.let { " $it" } ?: ""
            out += Finding(
                file, line, start, end, LEVEL[severity] ?: Level.WARNING, k.str("rule") ?: "secret",
                "${k.str("name")} (${LABEL[severity] ?: severity}): $masked.$why", k.str("fixPrompt") ?: "",
            )
        }
        for (el in root.getAsJsonArray("packages")) {
            val p = el.obj() ?: throw CheckOutputException("yof printed a package finding this plugin does not know.")
            val severity = p.str("severity") ?: continue
            if (!visibleIn(profile, severity)) continue
            val file = norm(p.str("file") ?: continue)
            val name = p.str("name") ?: continue
            val at = linesOf(file)?.let { findDependencyLine(it, name) }
            val version = p.str("version")?.let { "@$it" } ?: ""
            val registry = if (p.str("ecosystem") == "npm") "npm" else "PyPI"
            out += Finding(
                file, at?.first ?: 0, at?.second ?: 0, at?.third ?: 1, LEVEL[severity] ?: Level.WARNING, "package.${p.str("issue")}",
                "$name$version ($registry, ${LABEL[severity] ?: severity}): ${p.str("why") ?: p.str("message") ?: ""}", p.str("fixPrompt") ?: "",
            )
        }
        return out
    }

    /** The whole key on its line, from the CLI's 1-based column to the next quote, space or end of line. */
    fun keySpan(lineText: String?, column: Int, masked: String): Pair<Int, Int> {
        val start = maxOf(0, column - 1)
        if (lineText != null && start < lineText.length) {
            val m = Regex("^[^\\s\"'`,;)]+").find(lineText.substring(start))
            if (m != null) return start to start + m.value.length
        }
        return start to start + maxOf(1, masked.trimEnd('•').length)
    }

    /** Line, start and end of the first whole-word mention of a dependency in its manifest. */
    fun findDependencyLine(lines: List<String>, name: String): Triple<Int, Int, Int>? {
        val re = Regex("(^|[^A-Za-z0-9_.@/-])(" + Regex.escape(name) + ")(?=$|[^A-Za-z0-9_.-])", RegexOption.IGNORE_CASE)
        lines.forEachIndexed { i, l ->
            val m = re.find(l)
            if (m != null) {
                val start = m.range.first + m.groupValues[1].length
                return Triple(i, start, start + name.length)
            }
        }
        return null
    }

    private fun norm(f: String) = f.replace('\\', '/').removePrefix("./")
    private fun JsonElement.obj(): JsonObject? = if (isJsonObject) asJsonObject else null
    private fun JsonObject.str(k: String): String? = get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    private fun JsonObject.int(k: String): Int? = get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
}
