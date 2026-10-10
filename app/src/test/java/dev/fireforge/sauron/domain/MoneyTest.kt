package dev.fireforge.sauron.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun `plus refuses mixed currencies`() {
        val other = anotherCurrencyInstance()
        assertThrows(IllegalArgumentException::class.java) {
            Money(100, Currency.THB) + Money(100, other)
        }
    }

    @Test
    fun `minus refuses mixed currencies`() {
        val other = anotherCurrencyInstance()
        assertThrows(IllegalArgumentException::class.java) {
            Money(100, Currency.THB) - Money(100, other)
        }
    }

    @Test
    fun `display groups baht and pads satang`() {
        assertEquals("฿12,345.67", Money(1_234_567, Currency.THB).display())
        assertEquals("฿0.05", Money(5, Currency.THB).display())
        assertEquals("-฿1,000.00", Money(-100_000, Currency.THB).display())
    }

    // Currency is a single-value enum, so the mismatch guard is exercised with a
    // second instance conjured off the class; plus/minus only compare identity.
    private fun anotherCurrencyInstance(): Currency =
        (sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            .get(null) as sun.misc.Unsafe)
            .allocateInstance(Currency::class.java) as Currency
}
