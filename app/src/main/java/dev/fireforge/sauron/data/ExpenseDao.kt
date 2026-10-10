package dev.fireforge.sauron.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Transaction
    @Query("SELECT * FROM expenses ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseWithCategory>>

    @Query(
        """
        SELECT e.categoryId AS categoryId, c.name AS name, SUM(e.amountMinor) AS totalMinor
        FROM expenses e JOIN categories c ON c.id = e.categoryId
        WHERE e.occurredAt BETWEEN :from AND :to
        GROUP BY e.categoryId, c.name
        ORDER BY totalMinor DESC
        """,
    )
    fun totalByCategory(from: Instant, to: Instant): Flow<List<CategoryTotal>>
}
