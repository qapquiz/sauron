package dev.fireforge.sauron.ai

import dev.fireforge.sauron.domain.Bank
import dev.fireforge.sauron.domain.SlipParse
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaReplyTest {

    private val response =
        """{"amount": "49.00", "merchant": "บริษัท แมคไทย จำกัด", "date": "2026-12-11", "time": "18:43", "bank": "KBANK", "category": "Food & Drink"}"""

    @Test
    fun minifiedJsonParsesAndIgnoresUnknownKeys() {
        assertEquals(
            SlipParse.Parsed(
                amountMinor = 4900L,
                merchant = "บริษัท แมคไทย จำกัด",
                occurredAt = Instant.parse("2026-12-11T11:43:00Z"),
                bank = Bank.KBANK,
            ),
            GemmaReplyCodec.decode(response),
        )
    }

    @Test
    fun fencedJsonParsesTheSame() {
        val fenced = "```json\n$response\n```"
        assertEquals(GemmaReplyCodec.decode(response), GemmaReplyCodec.decode(fenced))
    }

    @Test
    fun jsonNullMerchantNeedsHelp() {
        val noMerchant =
            """{"amount": "49.00", "merchant": null, "date": "2026-12-11", "time": "18:43", "bank": null}"""
        assertEquals(
            SlipParse.NeedsHelp(listOf("merchant")),
            GemmaReplyCodec.decode(noMerchant),
        )
    }

    @Test
    fun missingAmountNeedsHelp() {
        assertEquals(
            SlipParse.NeedsHelp(listOf("amount")),
            GemmaReplyCodec.decode("""{"amount": null, "merchant": "ร้านค้า", "date": "2026-12-11", "time": "18:43"}"""),
        )
    }

    @Test
    fun absentFieldsNeedHelp() {
        assertEquals(
            SlipParse.NeedsHelp(listOf("amount", "datetime", "merchant")),
            GemmaReplyCodec.decode("{}"),
        )
    }

    @Test
    fun garbageNeedsUnparseable() {
        assertEquals(
            SlipParse.NeedsHelp(listOf("gemma_unparseable")),
            GemmaReplyCodec.decode("I could not read this slip, sorry!"),
        )
    }

    @Test
    fun bankNamesMatchTheEnumExactly() {
        val reply = """{"amount": "10.00", "merchant": "ร้านค้า", "date": "2026-12-11", "time": "10:00", "bank": "kbank"}"""
        val result = GemmaReplyCodec.decode(reply)
        assertTrue("lowercase bank must not match", result is SlipParse.Parsed && result.bank == null)
    }
}
