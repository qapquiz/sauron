package dev.fireforge.sauron.ai

import dev.fireforge.sauron.domain.Bank
import dev.fireforge.sauron.domain.SlipAmount
import dev.fireforge.sauron.domain.SlipParse
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class GemmaReply(
    val amount: String? = null,
    val merchant: String? = null,
    val date: String? = null,
    val time: String? = null,
    val bank: String? = null,
)

/**
 * Decodes the model's reply into a [SlipParse]. kotlinx.serialization maps JSON null and absent
 * fields to null identically on the JVM and on device, so a green JVM test means the device
 * behavior is green.
 */
object GemmaReplyCodec {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val REASON_AMOUNT = "amount"
    private const val REASON_DATETIME = "datetime"
    private const val REASON_MERCHANT = "merchant"
    private const val REASON_UNPARSEABLE = "gemma_unparseable"
    private val bangkok = ZoneOffset.ofHours(7)

    fun decode(raw: String): SlipParse {
        val reply = try {
            json.decodeFromString<GemmaReply>(slice(stripFences(raw)))
        } catch (t: Exception) {
            return SlipParse.NeedsHelp(listOf(REASON_UNPARSEABLE))
        }
        val reasons = mutableListOf<String>()
        val amountMinor = reply.amount?.trim()?.let { SlipAmount.toMinorOrNull(it) }
        if (amountMinor == null) reasons += REASON_AMOUNT
        val occurredAt = toInstant(reply.date?.trim(), reply.time?.trim())
        if (occurredAt == null) reasons += REASON_DATETIME
        val merchant = reply.merchant?.trim()?.takeIf { it.isNotEmpty() }
        if (merchant == null) reasons += REASON_MERCHANT
        val at = occurredAt
        val amount = amountMinor
        val name = merchant
        return if (reasons.isEmpty() && amount != null && at != null && name != null) {
            SlipParse.Parsed(
                amountMinor = amount,
                merchant = name,
                occurredAt = at,
                bank = Bank.entries.firstOrNull { it.name == reply.bank?.trim() },
            )
        } else {
            SlipParse.NeedsHelp(reasons)
        }
    }

    private fun stripFences(text: String): String =
        text.lineSequence().filterNot { it.trimStart().startsWith("```") }.joinToString("\n")

    private fun slice(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalArgumentException("no json object in response")
        return text.substring(start, end + 1)
    }

    private fun toInstant(date: String?, time: String?): Instant? {
        if (date == null || time == null) return null
        return try {
            LocalDate.parse(date).atTime(LocalTime.parse(time)).atOffset(bangkok).toInstant()
        } catch (t: java.time.format.DateTimeParseException) {
            null
        }
    }
}
