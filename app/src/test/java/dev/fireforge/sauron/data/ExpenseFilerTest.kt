package dev.fireforge.sauron.data

import dev.fireforge.sauron.domain.Bank
import dev.fireforge.sauron.domain.SlipParse
import dev.fireforge.sauron.domain.SlipSource
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseFilerTest {

    private class RecordingExpenses : ExpenseDao {
        val inserted = mutableListOf<ExpenseEntity>()
        override suspend fun insert(expense: ExpenseEntity): Long {
            inserted += expense
            return inserted.size.toLong()
        }
        override fun observeAll() = throw UnsupportedOperationException()
        override fun totalByCategory(from: Instant, to: Instant) = throw UnsupportedOperationException()
    }

    private class RecordingCaptures : CaptureDao {
        var statusUpdates = 0
        override suspend fun insert(entity: CaptureEntity) = 1L
        override fun observeAll() = throw UnsupportedOperationException()
        override suspend fun count() = 0
        override suspend fun pending(status: CaptureStatus) = emptyList<CaptureEntity>()
        override suspend fun gemmaPending(
            statuses: Collection<CaptureStatus>,
            maxAttempts: Int,
        ) = emptyList<CaptureEntity>()
        override suspend fun claim(id: Long, to: CaptureStatus, from: CaptureStatus) = 1
        override suspend fun claimForGemma(
            id: Long,
            to: CaptureStatus,
            from: Collection<CaptureStatus>,
        ) = 1
        override suspend fun updateStatus(id: Long, status: CaptureStatus) {
            statusUpdates++
        }
        override suspend fun parkAfterGemma(id: Long, to: CaptureStatus) = Unit
        override suspend fun releaseAll(from: CaptureStatus, to: CaptureStatus) = 0
    }

    private class FakeCategories(private val otherId: Long?) : CategoryDao {
        override suspend fun insertAll(categories: List<CategoryEntity>) = Unit
        override fun observeAll() = throw UnsupportedOperationException()
        override suspend fun count() = 0
        override suspend fun otherCategoryId() = otherId
        override suspend fun findIdByName(name: String): Long? = if (name == "Food & Drink") 7L else null
    }

    private fun filer(otherId: Long?): Pair<ExpenseFiler, Triple<RecordingExpenses, RecordingCaptures, FakeCategories>> {
        val expenses = RecordingExpenses()
        val captures = RecordingCaptures()
        val categories = FakeCategories(otherId)
        val f = ExpenseFiler(expenses, captures, categories) { it() }
        return f to Triple(expenses, captures, categories)
    }

    private fun parsed() = SlipParse.Parsed(
        amountMinor = 125000,
        merchant = "นางสาวปิยะดา วงศ์สุข",
        occurredAt = Instant.parse("2026-10-10T02:15:00Z"),
        bank = Bank.KBANK,
    )

    private fun capture() = CaptureEntity(imageUri = "content://x/1", takenAt = Instant.EPOCH, ocrText = "t", detectedAt = Instant.EPOCH)

    @Test
    fun `one call inserts one expense and marks the capture PARSED`() = runTest {
        val (f, deps) = filer(otherId = 3L)
        val filed = f.file(capture(), parsed(), SlipSource.ManualEntry)
        assertTrue(filed)
        assertEquals(1, deps.first.inserted.size)
        assertEquals(125000L, deps.first.inserted[0].amountMinor)
        assertEquals(Bank.KBANK.name, deps.first.inserted[0].bank)
        assertEquals(1, deps.second.statusUpdates)
    }

    @Test
    fun `no Other category files nothing`() = runTest {
        val (f, deps) = filer(otherId = null)
        val filed = f.file(capture(), parsed(), SlipSource.ManualEntry)
        assertFalse(filed)
        assertTrue(deps.first.inserted.isEmpty())
        assertEquals(0, deps.second.statusUpdates)
    }
}
