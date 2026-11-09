package io.yof.jetbrains

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class YofNextStepsTest {
    @Test
    fun saysOpensSoonUntilOrderingOpens() {
        assertFalse(YofNextSteps.ORDERING_OPEN)
        assertEquals("Get a signed report (opens soon)", YofNextSteps.signedReportLabel())
        assertEquals("Turn on Patch (opens soon)", YofNextSteps.patchLabel())
        assertEquals("Turn on Patch", YofNextSteps.patchLabel(open = true))
        assertEquals("https://app.yof.io/yof/orders", YofNextSteps.SIGNED_REPORT_URL)
        assertEquals("https://app.yof.io/yof/patch", YofNextSteps.PATCH_URL)
    }

    @Test
    fun joinsEveryFixPrompt() {
        assertEquals("One.", YofNextSteps.combinedPrompt(listOf("One.")))
        assertEquals("yof found 2 problems. Fix them one by one:\n\n1. One.\n\n2. Two.", YofNextSteps.combinedPrompt(listOf("One.", " ", "Two.")))
    }

    @Test
    fun shipsTheIconsAt40By40() {
        for (name in listOf("pluginIcon.svg", "pluginIcon_dark.svg")) {
            val svg = File("src/main/resources/META-INF/$name").readText()
            assertTrue(name, svg.contains("viewBox=\"0 0 40 40\""))
            assertTrue(name, svg.contains("width=\"40\" height=\"40\""))
        }
    }
}
