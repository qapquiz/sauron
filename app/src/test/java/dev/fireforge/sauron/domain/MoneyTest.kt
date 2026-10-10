package dev.fireforge.sauron.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun `plus adds minor units of the same currency`() {
        val sum = Money(10_000, Currency.THB) + Money(234, Currency.THB)
        assertEquals(10_234, sum.minor)
        assertEquals(Currency.THB, sum.currency)
    }

    @Test
    fun `minus subtracts minor units of the same currency`() {
        val diff = Money(1_000, Currency.THB) - Money(250, Currency.THB)
        assertEquals(750, diff.minor)
        assertEquals(Currency.THB, diff.currency)
    }

    @Test
    fun `display groups baht and pads satang`() {
        assertEquals("฿12,345.67", Money(1_234_567, Currency.THB).display())
        assertEquals("฿0.05", Money(5, Currency.THB).display())
        assertEquals("-฿1,000.00", Money(-100_000, Currency.THB).display())
    }
}
