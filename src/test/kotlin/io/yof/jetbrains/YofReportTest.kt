package io.yof.jetbrains

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YofReportTest {
    private val masked = listOf("sk", "_live_", "FwIw", "••••••••").joinToString("")
    private val json = """
        {"tool":"yof","version":"0.2.0","profile":"balanced","offline":true,
         "keys":[
           {"file":"src/config.ts","line":3,"column":24,"rule":"secret.stripe_live","name":"Stripe live secret key","severity":"fix_now","masked":"$masked",
            "why":"Anyone can use it to take payments.","fixPrompt":"The value starts like this (shortened on purpose): $masked"},
           {"file":"src/web.ts","line":1,"column":1,"rule":"public.stripe_publishable","name":"Stripe publishable key","severity":"info","masked":"pk_live_ab••"}
         ],
         "packages":[
           {"file":"package.json","name":"reqeusts-helper","ecosystem":"npm","issue":"made_up","severity":"fix_now","message":"This package doesn't exist on npm.","fixPrompt":"Do not invent a package name."}
         ],
         "counts":{"fix_now":2,"fix_week":0,"info":1},"failed":true}
    """.trimIndent()
    private val files = mapOf(
        "src/config.ts" to "// config\n\nexport const stripe = \"" + listOf("sk", "_live_", "x".repeat(24)).joinToString("") + "\";\n",
        "package.json" to "{\n  \"dependencies\": {\n    \"react\": \"^18\",\n    \"reqeusts-helper\": \"^1.0.0\"\n  }\n}\n",
    )

    @Test
    fun mapsKeysAndPackagesWithTheCliWords() {
        val list = YofReport.parse(json) { files[it] }
        assertEquals(listOf("secret.stripe_live", "package.made_up"), list.map { it.code })   // info hidden by balanced
        val key = list[0]
        assertEquals(Finding("src/config.ts", 2, 23, 23 + 32, Level.ERROR, "secret.stripe_live",
            "Stripe live secret key (fix now): $masked. Anyone can use it to take payments.", "The value starts like this (shortened on purpose): $masked"), key)
        assertEquals(Triple(3, 5, 5 + "reqeusts-helper".length), Triple(list[1].line, list[1].startColumn, list[1].endColumn))
        assertTrue(list.none { it.message.contains("x".repeat(24)) || it.fixPrompt.contains("x".repeat(24)) })
    }

    @Test
    fun refusesOutputItDoesNotKnow() {
        for (bad in listOf("yof checked 2 files", "{\"tool\":\"other\"}", "[]")) {
            try { YofReport.parse(bad); throw AssertionError("parsed: $bad") } catch (e: CheckOutputException) { /* expected */ }
        }
    }

    @Test
    fun profilesMatchTheCli() {
        assertFalse(YofReport.visibleIn("relaxed", "fix_week"))
        assertTrue(YofReport.visibleIn("balanced", "fix_week"))
        assertFalse(YofReport.visibleIn("balanced", "info"))
        assertTrue(YofReport.visibleIn("strict", "info"))
    }

    @Test
    fun argsAreOfflineByDefault() {
        val o = YofSettings.Options()
        assertEquals(listOf("check", ".", "--json", "--offline"), YofArgs.check(o))
        o.packageLookups = true
        o.profile = "strict"
        assertEquals(listOf("check", ".", "--json", "--profile", "strict"), YofArgs.check(o))
        assertEquals(listOf("attack", "http://localhost:3000", "--config", ".yof/attack.json", "--mode", "full", "--json"), YofArgs.attack(o))
    }

    @Test
    fun attackNeedsTheConsentWordForWord() {
        assertTrue(YofAttack.preflight(null, ".yof/attack.json")!!.startsWith("There is no .yof/attack.json yet"))
        assertTrue(YofAttack.preflight("{\"app\":\"localhost:3000\",\"consent\":\"sure\"}", "c")!!.contains(YofAttack.consentText("localhost:3000")))
        assertNull(YofAttack.preflight("{\"app\":\"localhost:3000\",\"consent\":\"${YofAttack.consentText("localhost:3000")}\"}", "c"))
        assertTrue(YofAttack.summarize("{\"findings\":[]}").last().endsWith("not that nothing can go wrong."))
    }
}
