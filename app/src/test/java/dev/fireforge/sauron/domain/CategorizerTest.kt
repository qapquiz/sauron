package dev.fireforge.sauron.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategorizerTest {

    @Test
    fun `food merchant hits food and drink`() {
        assertEquals("Food & Drink", Categorizer.categorize("แมคโดนัลด์ สาขาสยาม"))
    }

    @Test
    fun `transport merchant hits transport`() {
        assertEquals("Transport", Categorizer.categorize("GRAB Car รับส่ง"))
    }

    @Test
    fun `shopping merchant hits shopping`() {
        assertEquals("Shopping", Categorizer.categorize("Big C Extra ราชดำริ"))
    }

    @Test
    fun `bills merchant hits bills`() {
        assertEquals("Bills", Categorizer.categorize("ค่าไฟฟ้า เดือน ต.ค."))
    }

    @Test
    fun `income merchant hits income`() {
        assertEquals("Income", Categorizer.categorize("เงินเดือน ต.ค. 2569"))
    }

    @Test
    fun `personal name matches no rule`() {
        assertNull(Categorizer.categorize("นางสาวปิยะดา วงศ์สุข"))
    }

    @Test
    fun `company name with macthai hits food and drink`() {
        assertEquals("Food & Drink", Categorizer.categorize("บริษัท แมคไทย จำกัด"))
    }

    @Test
    fun `bangkok taxi hits transport via taxi keyword`() {
        assertEquals("Transport", Categorizer.categorize("Taxi กรุงเทพ"))
    }

    @Test
    fun `bank name alone matches no rule`() {
        assertNull(Categorizer.categorize("ธนาคารกรุงเทพ"))
    }

    @Test
    fun `empty merchant matches no rule`() {
        assertNull(Categorizer.categorize(""))
    }
}
