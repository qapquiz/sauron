package dev.fireforge.sauron.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MonthWindowTest {

    @Test
    fun `mid month instant in October 2026 at plus07 spans the month`() {
        val window = MonthWindow.current(Instant.parse("2026-10-15T03:30:00Z"), ZoneOffset.ofHours(7))

        assertEquals(Instant.parse("2026-09-30T17:00:00Z"), window.from)
        assertEquals(Instant.parse("2026-10-31T17:00:00Z"), window.to)
    }

    @Test
    fun `November 1 0030 plus07 boundary instant lands in November`() {
        val window = MonthWindow.current(Instant.parse("2026-10-31T17:30:00Z"), ZoneOffset.ofHours(7))

        assertEquals(Instant.parse("2026-10-31T17:00:00Z"), window.from)
        assertEquals(Instant.parse("2026-11-30T17:00:00Z"), window.to)
    }

    @Test
    fun `December instant rolls to January of the next year`() {
        val window = MonthWindow.current(Instant.parse("2026-12-25T10:00:00Z"), ZoneOffset.ofHours(7))

        assertEquals(Instant.parse("2026-11-30T17:00:00Z"), window.from)
        assertEquals(Instant.parse("2026-12-31T17:00:00Z"), window.to)
    }

    @Test
    fun `UTC now converted at Bangkok zone`() {
        val window = MonthWindow.current(Instant.parse("2026-11-02T04:00:00Z"), ZoneId.of("Asia/Bangkok"))

        assertEquals(Instant.parse("2026-10-31T17:00:00Z"), window.from)
        assertEquals(Instant.parse("2026-11-30T17:00:00Z"), window.to)
    }
}
