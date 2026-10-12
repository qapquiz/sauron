package dev.fireforge.sauron.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Half-open window over one calendar month in [zone]: [from] inclusive, [to] exclusive. */
data class MonthWindow(val from: Instant, val to: Instant) {

    companion object {
        fun current(now: Instant, zone: ZoneId): MonthWindow {
            val month = YearMonth.from(now.atZone(zone))
            val from = month.atDay(1).atStartOfDay(zone).toInstant()
            val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
            return MonthWindow(from, to)
        }
    }
}
