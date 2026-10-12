package dev.fireforge.sauron.ai

import dev.fireforge.sauron.domain.Bank
import dev.fireforge.sauron.domain.SlipParse
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class GemmaSlipJsonTest {

    private val response =
        """{"amount": "49.00", "merchant": "บริษัท แมคไทย จำกัด", "date": "2026-12-11", "time": "18:43", "bank": "KBANK", "category": "Food & Drink"}"""

    @Test
    fun minifiedJsonParses() {
        assertEquals(
            SlipParse.Parsed(
                amountMinor = 4900L,
                merchant = "บริษัท แมคไทย จำกัด",
                occurredAt = Instant.parse("2026-12-11T11:43:00Z"),
                bank = Bank.KBANK,
                notes = listOf("gemma_category:Food & Drink"),
            ),
            GemmaSlipJson.parse(response),
        )
    }

    @Test
    fun fencedJsonParsesTheSame() {
        val fenced = "```json\n$response\n```"
        assertEquals(GemmaSlipJson.parse(response), GemmaSlipJson.parse(fenced))
    }

    @Test
    fun missingMerchantNeedsHelp() {
        val noMerchant =
            """{"amount": "49.00", "merchant": null, "date": "2026-12-11", "time": "18:43", "bank": null}"""
        assertEquals(
            SlipParse.NeedsHelp(listOf("merchant")),
            GemmaSlipJson.parse(noMerchant),
        )
    }

    @Test
    fun missingAmountNeedsHelp() {
        assertEquals(
            SlipParse.NeedsHelp(listOf("amount")),
            GemmaSlipJson.parse("""{"amount": null, "merchant": "ร้านค้า", "date": "2026-12-11", "time": "18:43"}"""),
        )
    }

    @Test
    fun garbageNeedsUnparseable() {
        assertEquals(
            SlipParse.NeedsHelp(listOf("gemma_unparseable")),
            GemmaSlipJson.parse("I could not read this slip, sorry!"),
        )
    }
}
