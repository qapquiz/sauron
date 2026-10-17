package dev.fireforge.sauron.docs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DocsLintTest {

    private fun repoRoot(): File {
        val dir = File(System.getProperty("user.dir"))
        return if (dir.name == "app") dir.parentFile else dir
    }

    private fun docLines(): List<Pair<Int, String>> =
        File(repoRoot(), "docs/architecture.md").readLines().mapIndexed { i, l -> (i + 1) to l }

    @Test
    fun `no percentage or share claims without a facts anchor`() {
        val offenders = docLines().filter { (_, l) -> Regex("\\d+\\s*(%| percent\\b)|percent of").containsMatchIn(l) }
        assertTrue(
            "percent/share claims must anchor in docs/facts.md, found: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `ml kit appears only as the verified absence fact`() {
        val doc = File(repoRoot(), "docs/architecture.md").readText()
        assertTrue(
            "ML Kit dependency claims are banned; state the verified absence instead",
            !doc.contains("text-recognition-thai") && !doc.contains("mlkit"),
        )
        val mentions = Regex("ML Kit").findAll(doc).count()
        val anchored = File(repoRoot(), "docs/facts.md").readText().contains("ML Kit ships no Thai text recognizer")
        assertTrue("an ML Kit mention exists without its docs/facts.md anchor", mentions == 0 || anchored)
    }

    @Test
    fun `every ml kit mention has its anchor in facts md`() {
        val mentions = docLines().count { (_, l) -> l.contains("ML Kit") }
        val facts = File(repoRoot(), "docs/facts.md").readText()
        val anchored = facts.contains("ML Kit ships no Thai text recognizer")
        assertTrue("an ML Kit mention exists without its docs/facts.md anchor", mentions == 0 || anchored)
    }
}
