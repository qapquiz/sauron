package dev.fireforge.sauron.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SlipParserTest {

    private data class Golden(val name: String, val text: String, val expected: SlipParse)

    private val scbClean = """
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

    private val kbankClean = """
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

    private val promptpayClean = """
        พร้อมเพย์
        โอนสำเร็จ

        ผู้รับ: มูลนิธิช่วยชาวประมง
        เบอร์โทร x-x-x1234

        45.50 บาท

        วันเวลา: 09 ต.ค. 2026 18:44
        Ref: PP26091234450987
    """.trimIndent()

    private val scbNoise = scbClean
        .replace("สำเร็จ", "สําเร็จ")
        .replace("฿350.00", "8350.00")

    private val beYear = """
        ผู้รับ: ร้านส้มตำแม่ใจ
        จำนวนเงิน: ฿45.00
        วันที่: 11 ต.ค. 2569 14:32
    """.trimIndent()

    private val englishPartial = """
        Transfer Successful
        Amount: ฿99.00
        Date: 01/10/2026 08:05
    """.trimIndent()

    private val goldens = listOf(
        Golden(
            "scb_clean",
            scbClean,
            SlipParse.Parsed(35000, "ร้านอาหารครัวอร่อย", Instant.parse("2026-10-11T07:32:00Z"), Bank.SCB, emptyList()),
        ),
        Golden(
            "kbank_clean",
            kbankClean,
            SlipParse.Parsed(125000, "นางสาวปิยะดา วงศ์สุข", Instant.parse("2026-10-10T02:15:00Z"), Bank.KBANK, emptyList()),
        ),
        Golden(
            "promptpay_clean",
            promptpayClean,
            SlipParse.Parsed(4550, "มูลนิธิช่วยชาวประมง", Instant.parse("2026-10-09T11:44:00Z"), null, listOf("amount_fallback")),
        ),
        Golden("scb_ocr_noise", scbNoise, SlipParse.NeedsHelp(listOf("amount_ambiguous_baht_sign"))),
        Golden(
            "be_year",
            beYear,
            SlipParse.Parsed(4500, "ร้านส้มตำแม่ใจ", Instant.parse("2026-10-11T07:32:00Z"), null, emptyList()),
        ),
        Golden("english_partial", englishPartial, SlipParse.NeedsHelp(listOf("merchant"))),
        Golden("garbage", "asdf jkl", SlipParse.NeedsHelp(emptyList())),
    )

    @Test
    fun `golden cases parse as expected`() {
        for (golden in goldens) {
            when (val expected = golden.expected) {
                is SlipParse.Parsed -> assertEquals(golden.name, expected, SlipParser.parse(golden.text))
                is SlipParse.NeedsHelp -> {
                    val actual = SlipParser.parse(golden.text)
                    assertTrue(golden.name, actual is SlipParse.NeedsHelp)
                    assertTrue(golden.name, (actual as SlipParse.NeedsHelp).reasons.containsAll(expected.reasons))
                }
            }
        }
    }

    @Test
    fun `normalizer composes decomposed sara am`() {
        assertEquals("โอนเงินสำเร็จ", SlipNormalizer.normalize("โอนเงินสําเร็จ"))
    }

    @Test
    fun `noise case fails only on the ambiguous amount`() {
        val result = SlipParser.parse(scbNoise) as SlipParse.NeedsHelp
        assertEquals(listOf("amount_ambiguous_baht_sign"), result.reasons)
    }
}
