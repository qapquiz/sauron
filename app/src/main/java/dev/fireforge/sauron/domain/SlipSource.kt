package dev.fireforge.sauron.domain

import java.time.Instant

sealed interface SlipSource {
    data class ScreenCapture(val contentUri: String, val capturedAt: Instant) : SlipSource
    data object ManualEntry : SlipSource
}

object SlipSourceCodec {

    private const val SEP = "|"
    private const val CAPTURE = "CAPTURE"
    private const val MANUAL = "MANUAL"

    fun encode(source: SlipSource): String = when (source) {
        is SlipSource.ScreenCapture ->
            listOf(CAPTURE, source.contentUri, source.capturedAt.toEpochMilli()).joinToString(SEP)
        is SlipSource.ManualEntry -> MANUAL
    }

    fun decode(raw: String): SlipSource {
        val parts = raw.split(SEP)
        return when (parts[0]) {
            CAPTURE -> {
                require(parts.size >= 3) { "Malformed ScreenCapture: $raw" }
                SlipSource.ScreenCapture(
                    contentUri = parts.subList(1, parts.size - 1).joinToString(SEP),
                    capturedAt = Instant.ofEpochMilli(parts.last().toLong()),
                )
            }
            MANUAL -> {
                require(parts.size == 1) { "Malformed ManualEntry: $raw" }
                SlipSource.ManualEntry
            }
            else -> throw IllegalArgumentException("Unknown SlipSource: $raw")
        }
    }
}
