package dev.fireforge.sauron.domain

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

object SlipParser {

    private const val REASON_AMOUNT = "amount"
    private const val REASON_DATETIME = "datetime"
    private const val REASON_MERCHANT = "merchant"
    private const val REASON_AMBIGUOUS_BAHT = "amount_ambiguous_baht_sign"
    private const val NOTE_AMOUNT_FALLBACK = "amount_fallback"

    private val amountLabel = Regex("(จำนวนเงิน|จำนวน|amount)", RegexOption.IGNORE_CASE)
    private val amountToken = Regex("[0-9][0-9,]*(\\.[0-9]{2})?")
    private val amountFallback =
        Regex("(฿\\s*[0-9][0-9,]*(\\.[0-9]{2})?)|([0-9][0-9,]*\\.[0-9]{2}\\s*บาท)")
    private val datetime = Regex(
        "(\\d{1,2})\\s+(ม\\.ค\\.|ก\\.พ\\.|มี\\.ค\\.|เม\\.ย\\.|พ\\.ค\\.|มิ\\.ย\\.|ก\\.ค\\.|ส\\.ค\\.|ก\\.ย\\.|ต\\.ค\\.|พ\\.ย\\.|ธ\\.ค\\.)" +
            "\\s+(\\d{4}|\\d{2})\\s+(\\d{1,2}):(\\d{2})",
    )
    private val merchantLabel = Regex("^(ไปที่:|ถึงบัญชี:|ผู้รับ:)\\s*")
    private val monthAbbreviations = listOf(
        "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.",
    )
    private val bangkok = ZoneOffset.ofHours(7)

    fun parse(rawText: String): SlipParse {
        val text = SlipNormalizer.normalize(rawText)
        val reasons = mutableListOf<String>()
        var notes: List<String> = emptyList()

        var amountMinor: Long? = null
        val labelMatch = amountLabel.find(text)
        if (labelMatch == null) {
            val fallback = amountFallback.find(text)
            if (fallback == null) {
                reasons += REASON_AMOUNT
            } else {
                amountMinor = toMinor(amountToken.find(fallback.value)!!.value)
                notes = listOf(NOTE_AMOUNT_FALLBACK)
            }
        } else {
            val lineEnd = text.indexOf('\n', labelMatch.range.last)
                .let { if (it == -1) text.length else it }
            val token = amountToken.find(text.substring(labelMatch.range.last + 1, lineEnd))
            when {
                token == null -> reasons += REASON_AMOUNT
                // OCR often reads ฿ as 8, so a leading 8 could be either the sign or the digit
                token.value.startsWith("8") -> reasons += REASON_AMBIGUOUS_BAHT
                else -> amountMinor = toMinor(token.value)
            }
        }

        val occurredAt = datetime.find(text)?.let { match ->
            val month = monthAbbreviations.indexOf(match.groupValues[2]) + 1
            var year = match.groupValues[3].toInt()
            if (year < 100) year += 2500
            if (year > 2400) year -= 543
            OffsetDateTime.of(
                year,
                month,
                match.groupValues[1].toInt(),
                match.groupValues[4].toInt(),
                match.groupValues[5].toInt(),
                0,
                0,
                bangkok,
            ).toInstant()
        }
        if (occurredAt == null) reasons += REASON_DATETIME

        val merchant = text.lineSequence()
            .map { it.trim() }
            .firstNotNullOfOrNull { line ->
                merchantLabel.find(line)?.let { match ->
                    line.substring(match.value.length).trim().ifEmpty { null }
                }
            }
            // K PLUS bill payment slips name the shop with no label; the company line is the stable signal
                ?: text.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.contains("จำกัด") }
        if (merchant == null) reasons += REASON_MERCHANT

        val amount = amountMinor
        val at = occurredAt
        return if (reasons.isEmpty() && amount != null && at != null && merchant != null) {
            SlipParse.Parsed(amount, merchant, at, Bank.fromText(text), notes)
        } else {
            SlipParse.NeedsHelp(reasons)
        }
    }

    private fun toMinor(token: String): Long {
        val clean = token.replace(",", "")
        val dot = clean.indexOf('.')
        if (dot == -1) return clean.toLong() * 100
        return clean.substring(0, dot).toLong() * 100 +
            clean.substring(dot + 1).padEnd(2, '0').toLong()
    }
}
