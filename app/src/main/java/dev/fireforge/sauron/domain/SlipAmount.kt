package dev.fireforge.sauron.domain

/** Baht token to minor units. One parser for OCR text and model replies; null unless the token has 0-2 decimals. */
object SlipAmount {

    fun toMinorOrNull(token: String): Long? {
        val clean = token.replace(",", "")
        if (clean.isEmpty()) return null
        val dot = clean.indexOf('.')
        val whole = clean.substring(0, if (dot == -1) clean.length else dot).toLongOrNull() ?: return null
        val satang = when {
            dot == -1 -> 0L
            else -> {
                val frac = clean.substring(dot + 1)
                if (frac.length > 2 || frac.isEmpty()) return null
                frac.padEnd(2, '0').toLongOrNull() ?: return null
            }
        }
        return whole * 100 + satang
    }
}
