package dev.fireforge.sauron.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DaoStatusLiteralTest {

    @Test
    fun `capture dao sql contains no hard coded status literals`() {
        val dir = File(System.getProperty("user.dir"))
        val root = if (dir.name == "app") dir.parentFile else dir
        val src = File(root, "app/src/main/java/dev/fireforge/sauron/data/CaptureDao.kt").readText()
        val literals = CaptureStatus.entries
            .map { "'${it.name}'" }
            .filter { src.contains(it) }
        assertTrue(
            "CaptureDao SQL hard-codes status names $literals; bind CaptureStatus params so a rename cannot silently break queries",
            literals.isEmpty(),
        )
    }
}
