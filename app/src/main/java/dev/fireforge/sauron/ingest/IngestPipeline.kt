package dev.fireforge.sauron.ingest

import dev.fireforge.sauron.ai.GemmaReader
import dev.fireforge.sauron.ai.GemmaSlipReader
import dev.fireforge.sauron.data.CaptureDao
import dev.fireforge.sauron.data.CaptureEntity
import dev.fireforge.sauron.data.CaptureStatus
import dev.fireforge.sauron.data.ExpenseFiler
import dev.fireforge.sauron.domain.SlipParse
import dev.fireforge.sauron.domain.SlipParser
import dev.fireforge.sauron.domain.SlipRules
import dev.fireforge.sauron.domain.SlipSource
import java.time.Instant

/**
 * One ingest pass: scan the watched folder, score and insert captures, then run the parse stage
 * and the Gemma stage. Owns the claim loop once. Observability goes through [RunLog].
 */
class IngestPipeline(
    private val source: ImageSource,
    private val ocr: OcrReader,
    private val gemma: GemmaReader,
    private val captures: CaptureDao,
    private val filer: ExpenseFiler,
    private val otherCategoryId: suspend () -> Long?,
) {

    data class RunReport(val seen: Int, val decoded: Int, val slips: Int, val filed: Int, val parked: Int, val maxSeen: Long)

    suspend fun run(watermark: Long): RunReport {
        val stranded = captures.releaseAll(CaptureStatus.PARSING, CaptureStatus.CAPTURED)
        if (stranded > 0) RunLog.strandedReleased(stranded)
        var maxSeen = watermark
        var seen = 0
        var decoded = 0
        var slips = 0
        for (image in source.query(watermark)) {
            if (image.dateAdded > maxSeen) maxSeen = image.dateAdded
            seen++
            val text = ocr.recognize(image.uri) ?: continue
            decoded++
            val score = SlipRules.score(text)
            val status = when {
                SlipRules.isSlip(score) -> CaptureStatus.CAPTURED
                score.total >= SlipRules.gemmaFloor -> CaptureStatus.NEEDS_GEMMA
                else -> null
            }
            if (status != null) {
                slips++
                captures.insert(
                    CaptureEntity(
                        imageUri = image.uri,
                        takenAt = Instant.ofEpochSecond(image.dateAdded),
                        ocrText = text,
                        detectedAt = Instant.now(),
                        status = status,
                    ),
                )
            }
        }
        val (parsed, parked) = parseStage()
        val (gemmaParsed, gemmaNeedsHelp) = gemmaStage()
        return RunReport(seen, decoded, slips, parsed + gemmaParsed, parked + gemmaNeedsHelp, maxSeen)
    }

    private suspend fun parseStage(): Pair<Int, Int> {
        val otherId = otherCategoryId() ?: return 0 to 0
        var parsed = 0
        var needsHelp = 0
        for (capture in captures.pending()) {
            if (captures.claim(capture.id, CaptureStatus.PARSING) != 1) continue
            when (val result = SlipParser.parse(capture.ocrText)) {
                is SlipParse.Parsed -> {
                    if (filer.file(capture, result, SlipSource.ScreenCapture(capture.imageUri, capture.takenAt))) {
                        parsed++
                    }
                }
                is SlipParse.NeedsHelp -> {
                    captures.updateStatus(capture.id, CaptureStatus.NO_PARSE)
                    RunLog.captureNeedsHelp(capture.id, "parse", result.reasons)
                    needsHelp++
                }
            }
        }
        RunLog.parseDone(parsed, needsHelp)
        return parsed to needsHelp
    }

    private suspend fun gemmaStage(): Pair<Int, Int> {
        val otherId = otherCategoryId() ?: return 0 to 0
        var parsed = 0
        var needsHelp = 0
        for (capture in captures.gemmaPending()) {
            if (captures.claimForGemma(capture.id, CaptureStatus.PARSING) != 1) continue
            val bytes = source.readOriginalBytes(capture.imageUri)
            if (bytes == null) {
                captures.parkAfterGemma(capture.id)
                RunLog.unreadableImage(capture.imageUri, "gemma")
                needsHelp++
                continue
            }
            when (val result = gemma.readSlip(bytes)) {
                is SlipParse.Parsed -> {
                    if (filer.file(capture, result, SlipSource.ScreenCapture(capture.imageUri, capture.takenAt))) {
                        parsed++
                    }
                }
                is SlipParse.NeedsHelp -> {
                    captures.parkAfterGemma(capture.id)
                    RunLog.captureNeedsHelp(capture.id, "gemma", result.reasons)
                    needsHelp++
                }
            }
        }
        RunLog.gemmaDone(parsed, needsHelp)
        return parsed to needsHelp
    }
}
