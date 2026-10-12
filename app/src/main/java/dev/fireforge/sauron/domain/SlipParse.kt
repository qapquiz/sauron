package dev.fireforge.sauron.domain

import java.time.Instant

sealed interface SlipParse {
    data class Parsed(
        val amountMinor: Long,
        val merchant: String,
        val occurredAt: Instant,
        val bank: Bank?,
        val notes: List<String>,
    ) : SlipParse

    data class NeedsHelp(val reasons: List<String>) : SlipParse
}
