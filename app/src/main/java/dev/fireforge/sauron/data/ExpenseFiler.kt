package dev.fireforge.sauron.data

import androidx.room.withTransaction
import dev.fireforge.sauron.domain.Categorizer
import dev.fireforge.sauron.domain.Currency
import dev.fireforge.sauron.domain.SlipParse
import dev.fireforge.sauron.domain.SlipSource
import java.time.Instant

/** Runs a block inside the database write transaction. Production wires Room's withTransaction here. */
fun interface Tx {
    suspend fun run(block: suspend () -> Unit)
}

/**
 * The only code that inserts an expense. The expense row and the capture's PARSED status commit
 * atomically, so a crash mid-write never leaves one without the other.
 *
 * Callers that iterate captures should skip their stage when [CategoryDao.otherCategoryId] is null,
 * before claiming anything; the guard here keeps a raced claim from sticking.
 */
class ExpenseFiler(
    private val expenses: ExpenseDao,
    private val captures: CaptureDao,
    private val categories: CategoryDao,
    private val tx: Tx,
) {

    suspend fun file(capture: CaptureEntity, parsed: SlipParse.Parsed, source: SlipSource): Boolean {
        val otherId = categories.otherCategoryId() ?: return false
        val categoryId = Categorizer.categorize(parsed.merchant)
            ?.let { categories.findIdByName(it) }
            ?: otherId
        tx.run {
            expenses.insert(
                ExpenseEntity(
                    merchant = parsed.merchant,
                    amountMinor = parsed.amountMinor,
                    currency = Currency.THB,
                    categoryId = categoryId,
                    occurredAt = parsed.occurredAt,
                    bank = parsed.bank?.name,
                    source = source,
                    createdAt = Instant.now(),
                ),
            )
            captures.updateStatus(capture.id, CaptureStatus.PARSED)
        }
        return true
    }
}
