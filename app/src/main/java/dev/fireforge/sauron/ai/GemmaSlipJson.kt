package dev.fireforge.sauron.ai

import dev.fireforge.sauron.domain.Bank
import dev.fireforge.sauron.domain.SlipParse
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import org.json.JSONException
import org.json.JSONObject

object GemmaSlipJson {

    private const val REASON_AMOUNT = "amount"
    private const val REASON_DATETIME = "datetime"
    private const val REASON_MERCHANT = "merchant"
    private const val REASON_UNPARSEABLE = "gemma_unparseable"
    private const val NOTE_CATEGORY_PREFIX = "gemma_category:"
    private val bangkok = ZoneOffset.ofHours(7)

    fun parse(response: String): SlipParse {
        val json = try {
            JSONObject(slice(stripFences(response)))
        } catch (t: JSONException) {
            return SlipParse.NeedsHelp(listOf(REASON_UNPARSEABLE))
        }

        val reasons = mutableListOf<String>()
        val notes = mutableListOf<String>()

        val amountMinor = json.optString("amount").trim().takeIf { it.isNotEmpty() }?.let(::toMinorOrNull)
        if (amountMinor == null) reasons += REASON_AMOUNT

        json.optString("category").trim().takeIf { it.isNotEmpty() }?.let { notes += NOTE_CATEGORY_PREFIX + it }

        val occurredAt = toInstant(json.optString("date"), json.optString("time"))
        if (occurredAt == null) reasons += REASON_DATETIME

        val merchant = json.optString("merchant").trim().takeIf { it.isNotEmpty() }
        if (merchant == null) reasons += REASON_MERCHANT

        val amount = amountMinor
        val at = occurredAt
        val name = merchant
        return if (reasons.isEmpty() && amount != null && at != null && name != null) {
            SlipParse.Parsed(
                amountMinor = amount,
                merchant = name,
                occurredAt = at,
                bank = Bank.entries.firstOrNull { it.name == json.optString("bank") },
                notes = notes,
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
        if (start < 0 || end <= start) throw JSONException("no json object in response")
        return text.substring(start, end + 1)
    }

    private fun toMinorOrNull(token: String): Long? = try {
        val clean = token.replace(",", "")
        val dot = clean.indexOf('.')
        if (dot == -1) clean.toLong() * 100
        else clean.substring(0, dot).toLong() * 100 + clean.substring(dot + 1).padEnd(2, '0').toLong()
    } catch (t: NumberFormatException) {
        null
    }

    private fun toInstant(date: String, time: String): Instant? = try {
        LocalDate.parse(date.trim()).atTime(LocalTime.parse(time.trim())).atOffset(bangkok).toInstant()
    } catch (t: DateTimeParseException) {
        null
    }
}
