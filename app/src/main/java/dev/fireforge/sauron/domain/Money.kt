package dev.fireforge.sauron.domain

import kotlin.math.abs

enum class Currency(val symbol: String) {
    THB("฿"),
}

data class Money(val minor: Long, val currency: Currency) {

    operator fun plus(other: Money): Money {
        require(other.currency == currency) { "Cannot add ${other.currency} to $currency" }
        return copy(minor = minor + other.minor)
    }

    operator fun minus(other: Money): Money {
        require(other.currency == currency) { "Cannot subtract ${other.currency} from $currency" }
        return copy(minor = minor - other.minor)
    }

    fun display(): String {
        val sign = if (minor < 0) "-" else ""
        val whole = abs(minor) / 100
        val satang = abs(minor) % 100
        val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
        return "$sign${currency.symbol}$grouped.${satang.toString().padStart(2, '0')}"
    }
}
