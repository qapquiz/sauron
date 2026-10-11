package dev.fireforge.sauron.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlipRulesTest {

    private val scbTransfer = """
        ธนาคารไทยพาณิชย์
        โอนเงินสำเร็จ
        Transfer Successful

        จาก: นายสมชาย ใจดี
        พร้อมเพย์ 0-9876-543-2
        ไปที่: ร้านอาหารครัวอร่อย
        พร้อมเพย์ 1-2345-678-9

        จำนวนเงิน: ฿350.00
        ค่าธรรมเนียม: ฿0.00

        วันที่: 11 ต.ค. 2026 14:32
        เลขอ้างอิง: 20261011000123456789
    """.trimIndent()

    private val kbankTransfer = """
        K PLUS by Kasikornbank
        โอนเงินผ่าน K PLUS
        Transfer via K PLUS

        ถึงบัญชี: นางสาวปิยะดา วงศ์สุข
        ธนาคารกสิกรไทย
        บัญชีออมทรัพย์ x-x-x-x-x8901

        จำนวนเงิน 1,250.00 บาท
        ค่าธรรมเนียม 0.00 บาท

        วันที่ทำรายการ: 10 ต.ค. 2026 09:15
        เลขที่อ้างอิง: KT26101009150042
    """.trimIndent()

    private val promptpayDark = """
        พร้อมเพย์
        โอนสำเร็จ

        ผู้รับ: มูลนิธิช่วยชาวประมง
        เบอร์โทร x-x-x1234

        45.50 บาท

        วันเวลา: 09 ต.ค. 2026 18:44
        Ref: PP26091234450987
    """.trimIndent()

    private val chatNegative = """
        LINE
        แม่: เย็นนี้กินข้าวที่ไหน
        ผม: ที่บ้านก็ได้ครับ
        แม่: ซื้อผักมาด้วยนะ
        ผม: เดี๋ยวโอนให้ครับ อิ่มแล้ว
        แม่: อย่าลืมซื้อนม
        ผม: ครับ
    """.trimIndent()

    private val settingsNegative = """
        Settings
        Settings
        Network & internet
        Connected devices
        Apps
        Notifications
        Battery
        Storage
    """.trimIndent()

    @Test
    fun `scb transfer scores as a slip`() {
        val score = SlipRules.score(scbTransfer)
        assertTrue(SlipRules.isSlip(score))
    }

    @Test
    fun `kbank transfer scores as a slip`() {
        val score = SlipRules.score(kbankTransfer)
        assertTrue(SlipRules.isSlip(score))
    }

    @Test
    fun `promptpay dark slip scores as a slip`() {
        val score = SlipRules.score(promptpayDark)
        assertTrue(SlipRules.isSlip(score))
    }

    @Test
    fun `chat screenshot scores below threshold`() {
        assertFalse(SlipRules.isSlip(SlipRules.score(chatNegative)))
    }

    @Test
    fun `settings screen scores below threshold`() {
        assertFalse(SlipRules.isSlip(SlipRules.score(settingsNegative)))
    }

    @Test
    fun `empty text scores below threshold`() {
        assertFalse(SlipRules.isSlip(SlipRules.score("")))
    }

    @Test
    fun `scb text matches the amount rule`() {
        assertTrue(SlipRules.score(scbTransfer).matched.contains("amount"))
    }
}
