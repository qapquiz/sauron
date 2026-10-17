package dev.fireforge.sauron.ai

import dev.fireforge.sauron.domain.SlipParse
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaDecimalTest {

    @Test
    fun `three decimal amount is rejected, not mis-scaled`() {
        val reply = """{"amount":"49.555","merchant":"X","date":"2026-10-12","time":"10:00","bank":null}"""
        val result = GemmaSlipJson.parse(reply)
        assertTrue("expected NeedsHelp but got $result", result is SlipParse.NeedsHelp)
    }
}
