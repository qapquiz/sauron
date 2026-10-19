package dev.fireforge.sauron.domain

import java.io.File
import java.time.Instant

/** Loads golden slip text and expectations from testdata, the single home of slip text. */
object Fixtures {

    fun slipText(name: String): String = file("testdata/slips/text/$name.txt").readText()

    fun parserCaseText(name: String): String = file("testdata/slips/text/parser/$name.txt").readText()

    fun expectedScore(name: String): String = file("testdata/slips/expected/score/$name.expected").readText().trim()

    fun expectedParse(name: String): SlipParse {
        val line = file("testdata/slips/expected/parser/$name.expected").readText().trim()
        return if (line.startsWith("Parsed ")) {
            val f = line.removePrefix("Parsed ").split(" | ")
            SlipParse.Parsed(
                amountMinor = f[0].toLong(),
                merchant = f[1],
                occurredAt = Instant.parse(f[2]),
                bank = f[3].takeIf { it != "NONE" }?.let { Bank.valueOf(it) },
                notes = f.getOrElse(4) { "-" }.split(",").filter { it != "-" },
            )
        } else {
            SlipParse.NeedsHelp(line.removePrefix("NeedsHelp ").split(",").filter { it != "-" })
        }
    }

    fun scoreCaseNames(): List<String> =
        dir("testdata/slips/text").map { it.nameWithoutExtension }.sorted()

    fun parserCaseNames(): List<String> =
        dir("testdata/slips/text/parser").map { it.nameWithoutExtension }.sorted()

    fun repoRoot(): File {
        val dir = File(System.getProperty("user.dir"))
        return if (dir.name == "app") dir.parentFile else dir
    }

    private fun dir(path: String): List<File> =
        file(path).listFiles { f -> f.isFile && f.extension == "txt" }?.sortedBy { it.name }
            ?: error("missing fixture directory $path")

    private fun file(path: String): File = File(repoRoot(), path)
}
