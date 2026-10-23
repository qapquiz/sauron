package dev.fireforge.sauron.domain

data class SlipRule(val name: String, val pattern: Regex, val weight: Int)

data class SlipScore(val total: Int, val matched: List<String>)

object SlipRules {

    val threshold = 4

    val rules: List<SlipRule> = listOf(
        SlipRule(
            name = "transfer_words",
            pattern = Regex(
                "(โอนเงิน|โอนสำเร็จ|ถอนเงิน|ฝากเงิน|transfer (successful|completed)|payment successful)",
                RegexOption.IGNORE_CASE,
            ),
            weight = 2,
        ),
        SlipRule(
            name = "bank_terms",
            pattern = Regex(
                "(ธนาคาร|พร้อมเพย์|promptpay|กสิกร|ไทยพาณิชย์|กรุงเทพ|กรุงไทย|กรุงศรี|ทีเอ็มบี|กสิกรไทย|scb|kbank|k plus|คลิกซ์|clicx)",
                RegexOption.IGNORE_CASE,
            ),
            weight = 2,
        ),
        SlipRule(
            name = "amount",
            pattern = Regex("(฿|บาท)\\s*[0-9][0-9,]*(\\.[0-9]{2})?|[0-9][0-9,]*\\.[0-9]{2}\\s*(บาท|฿)"),
            weight = 1,
        ),
        SlipRule(
            name = "slip_meta",
            pattern = Regex(
                "(เลข(ที่)?อ้างอิง|วันที่(ทำรายการ)?|เวลา|reference|ref:)",
                RegexOption.IGNORE_CASE,
            ),
            weight = 1,
        ),
    )

    fun score(text: String): SlipScore {
        val matched = rules.filter { it.pattern.containsMatchIn(text) }
        return SlipScore(matched.sumOf { it.weight }, matched.map { it.name })
    }

    fun isSlip(score: SlipScore) = score.total >= threshold
}
