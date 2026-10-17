package dev.fireforge.sauron.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SlipParserTest {

    @Test
    fun `parser cases parse as their expected files say`() {
        for (name in Fixtures.parserCaseNames()) {
            val expected = Fixtures.expectedParse(name)
            val actual = SlipParser.parse(Fixtures.parserCaseText(name))
            when (val want = expected) {
                is SlipParse.Parsed -> assertEquals(name, want, actual)
                is SlipParse.NeedsHelp -> {
                    assertTrue(name, actual is SlipParse.NeedsHelp)
                    assertTrue(name, (actual as SlipParse.NeedsHelp).reasons.containsAll(want.reasons))
                }
            }
        }
    }

    @Test
    fun `every parser case has an expected file and the reverse`() {
        val texts = Fixtures.parserCaseNames()
        val expected = File(Fixtures.repoRoot(), "testdata/slips/expected/parser")
            .listFiles { f -> f.isFile && f.extension == "expected" }!!
            .map { it.nameWithoutExtension }
            .sorted()
        assertEquals(texts, expected)
    }

    @Test
    fun `normalizer composes decomposed sara am`() {
        assertEquals("โอนเงินสำเร็จ", SlipNormalizer.normalize("โอนเงินสําเร็จ"))
    }
}
