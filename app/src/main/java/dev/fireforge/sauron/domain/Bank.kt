package dev.fireforge.sauron.domain

enum class Bank(val matchers: List<String>) {
    SCB(listOf("ไทยพาณิชย์", "scb")),
    KBANK(listOf("กสิกร", "kbank", "k plus")),
    BBL(listOf("กรุงเทพ")),
    KTB(listOf("กรุงไทย")),
    BAY(listOf("กรุงศรี")),
    TTB(listOf("ทีเอ็มบี", "tmb")),
    GSB(listOf("ออมสิน")),
    CLICX(listOf("คลิกซ์", "clicx")),
    ;

    companion object {
        fun fromText(text: String): Bank? = entries.firstOrNull { bank ->
            bank.matchers.any { text.contains(it, ignoreCase = true) }
        }
    }
}
