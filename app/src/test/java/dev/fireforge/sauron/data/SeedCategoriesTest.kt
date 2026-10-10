package dev.fireforge.sauron.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SeedCategoriesTest {

    @Test
    fun `six default categories present`() {
        assertEquals(
            listOf("Food & Drink", "Transport", "Shopping", "Bills", "Income", "Other"),
            SeedCategories.map { it.name },
        )
    }

    @Test
    fun `names are unique`() {
        val names = SeedCategories.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }
}
