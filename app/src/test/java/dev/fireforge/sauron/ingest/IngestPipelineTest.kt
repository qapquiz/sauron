package dev.fireforge.sauron.ingest

import android.graphics.Bitmap
import dev.fireforge.sauron.ai.GemmaReader
import dev.fireforge.sauron.data.CaptureDao
import dev.fireforge.sauron.data.CaptureEntity
import dev.fireforge.sauron.data.CaptureStatus
import dev.fireforge.sauron.data.CategoryDao
import dev.fireforge.sauron.data.CategoryEntity
import dev.fireforge.sauron.data.ExpenseDao
import dev.fireforge.sauron.data.ExpenseEntity
import dev.fireforge.sauron.data.ExpenseFiler
import dev.fireforge.sauron.domain.Fixtures
import dev.fireforge.sauron.domain.SlipParse
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IngestPipelineTest {

    private class FakeSource(
        private val images: List<MediaStoreScanner.ScannedImage>,
        private val bytes: Map<String, ByteArray> = emptyMap(),
    ) : ImageSource {
        override fun query(watermark: Long) = images.filter { it.dateAdded > watermark }
        override fun decode(uri: String): Bitmap? = null
        override fun readOriginalBytes(uri: String) = bytes[uri]
    }

    private class FakeOcr(private val texts: Map<String, String>) : OcrReader {
        override fun recognize(uri: String) = texts[uri]
    }

    private class FakeGemma(private val result: SlipParse) : GemmaReader {
        var calls = 0
        override fun readSlip(imageBytes: ByteArray): SlipParse {
            calls++
            return result
        }
    }

    private class MemoryCaptures : CaptureDao {
        val rows = mutableListOf<CaptureEntity>()
        override suspend fun insert(entity: CaptureEntity): Long {
            rows += entity.copy(id = rows.size + 1L)
            return rows.size.toLong()
        }
        override fun observeAll() = throw UnsupportedOperationException()
        override suspend fun count() = rows.size
        override suspend fun pending(status: CaptureStatus) = rows.filter { it.status == status }
        override suspend fun gemmaPending(
            statuses: Collection<CaptureStatus>,
            maxAttempts: Int,
        ) = rows.filter { it.status in statuses && it.gemmaAttempts < maxAttempts }
        override suspend fun claim(id: Long, to: CaptureStatus, from: CaptureStatus): Int {
            val i = rows.indexOfFirst { it.id == id && it.status == from }
            if (i == -1) return 0
            rows[i] = rows[i].copy(status = to)
            return 1
        }
        override suspend fun claimForGemma(
            id: Long,
            to: CaptureStatus,
            from: Collection<CaptureStatus>,
        ): Int {
            val i = rows.indexOfFirst { it.id == id && it.status in from }
            if (i == -1) return 0
            rows[i] = rows[i].copy(status = to)
            return 1
        }
        override suspend fun updateStatus(id: Long, status: CaptureStatus) {
            val i = rows.indexOfFirst { it.id == id }
            if (i != -1) rows[i] = rows[i].copy(status = status)
        }
        override suspend fun parkAfterGemma(id: Long, to: CaptureStatus) {
            val i = rows.indexOfFirst { it.id == id }
            if (i != -1) rows[i] = rows[i].copy(status = to, gemmaAttempts = rows[i].gemmaAttempts + 1)
        }
        override suspend fun releaseAll(from: CaptureStatus, to: CaptureStatus): Int {
            var n = 0
            for (i in rows.indices) {
                if (rows[i].status == from) {
                    rows[i] = rows[i].copy(status = to)
                    n++
                }
            }
            return n
        }
    }

    private class RecordingExpenses : ExpenseDao {
        val inserted = mutableListOf<ExpenseEntity>()
        override suspend fun insert(expense: ExpenseEntity): Long {
            inserted += expense
            return inserted.size.toLong()
        }
        override fun observeAll() = throw UnsupportedOperationException()
        override fun totalByCategory(from: Instant, to: Instant) = throw UnsupportedOperationException()
    }

    private class AllCategories : CategoryDao {
        override suspend fun insertAll(categories: List<CategoryEntity>) = Unit
        override fun observeAll() = throw UnsupportedOperationException()
        override suspend fun count() = 1
        override suspend fun otherCategoryId() = 3L
        override suspend fun findIdByName(name: String): Long? = null
    }

    private fun pipeline(
        source: ImageSource,
        ocr: OcrReader,
        gemma: GemmaReader,
        captures: MemoryCaptures,
        expenses: RecordingExpenses,
    ): IngestPipeline =
        IngestPipeline(
            source = source,
            ocr = ocr,
            gemma = gemma,
            captures = captures,
            filer = ExpenseFiler(expenses, captures, AllCategories()) { it() },
            otherCategoryId = { 3L },
        )

    private fun capture(status: CaptureStatus, attempts: Int = 0) = CaptureEntity(
        imageUri = "content://x/$attempts-${status.name}",
        takenAt = Instant.EPOCH,
        ocrText = "seed",
        detectedAt = Instant.EPOCH,
        status = status,
        gemmaAttempts = attempts,
    )

    @Test
    fun `one pass scans, files slips, rejects noise, and advances the watermark`() = runTest {
        val captures = MemoryCaptures()
        val expenses = RecordingExpenses()
        val images = listOf(
            MediaStoreScanner.ScannedImage("content://slip", 100L),
            MediaStoreScanner.ScannedImage("content://noise", 90L),
        )
        val ocr = FakeOcr(
            mapOf(
                "content://slip" to Fixtures.parserCaseText("kbank_clean"),
                "content://noise" to Fixtures.parserCaseText("garbage"),
            ),
        )
        val report = pipeline(FakeSource(images), ocr, FakeGemma(SlipParse.NeedsHelp(listOf("x"))), captures, expenses)
            .run(0L)

        assertEquals(2, report.seen)
        assertEquals(2, report.decoded)
        assertEquals(1, report.slips)
        assertEquals(100L, report.maxSeen)
        assertEquals(1, expenses.inserted.size)
        assertEquals(125000L, expenses.inserted[0].amountMinor)
        assertEquals(CaptureStatus.PARSED, captures.rows.single { it.imageUri == "content://slip" }.status)
        assertEquals(0, captures.rows.count { it.imageUri == "content://noise" })
    }

    @Test
    fun `a pass releases captures stranded in PARSING and files them exactly once`() = runTest {
        val captures = MemoryCaptures()
        val expenses = RecordingExpenses()
        captures.insert(
            CaptureEntity(
                imageUri = "content://stranded",
                takenAt = Instant.EPOCH,
                ocrText = Fixtures.parserCaseText("kbank_clean"),
                detectedAt = Instant.EPOCH,
                status = CaptureStatus.PARSING,
            ),
        )
        val report = pipeline(FakeSource(emptyList()), FakeOcr(emptyMap()), FakeGemma(SlipParse.NeedsHelp(listOf("x"))), captures, expenses)
            .run(0L)

        assertEquals(1, report.filed)
        assertEquals(CaptureStatus.PARSED, captures.rows.single().status)
        assertEquals(1, expenses.inserted.size)
    }

    @Test
    fun `gemma stage files mid-score captures and respects the attempt cap`() = runTest {
        val captures = MemoryCaptures()
        val expenses = RecordingExpenses()
        captures.insert(capture(CaptureStatus.NEEDS_GEMMA))
        captures.insert(capture(CaptureStatus.NO_PARSE, attempts = CaptureStatus.MAX_GEMMA_ATTEMPTS))
        val gemma = FakeGemma(
            SlipParse.Parsed(4900L, "บริษัท แมคไทย จำกัด", Instant.parse("2026-12-11T11:43:00Z"), null, emptyList()),
        )
        val source = FakeSource(emptyList(), bytes = mapOf("content://x/0-NEEDS_GEMMA" to ByteArray(0)))
        val report = pipeline(source, FakeOcr(emptyMap()), gemma, captures, expenses).run(0L)

        assertEquals(1, gemma.calls)
        assertEquals(1, report.filed)
        assertEquals(CaptureStatus.PARSED, captures.rows[0].status)
        assertEquals(CaptureStatus.NO_PARSE, captures.rows[1].status)
        assertTrue(captures.rows[1].gemmaAttempts >= CaptureStatus.MAX_GEMMA_ATTEMPTS)
    }
}
