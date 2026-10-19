package dev.fireforge.sauron.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SlipRulesTest {

    @Test
    fun `fixture scores match their expected verdicts`() {
        for (name in Fixtures.scoreCaseNames()) {
            val score = SlipRules.score(Fixtures.slipText(name))
            assertEquals(name, Fixtures.expectedScore(name) == "slip", SlipRules.isSlip(score))
        }
    }

    @Test
    fun `every fixture has an expected verdict and the reverse`() {
        val texts = Fixtures.scoreCaseNames()
        val expected = File(Fixtures.repoRoot(), "testdata/slips/expected/score")
            .listFiles { f -> f.isFile && f.extension == "expected" }!!
            .map { it.nameWithoutExtension }
            .sorted()
        assertEquals(texts, expected)
    }
}
