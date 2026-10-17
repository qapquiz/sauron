package dev.fireforge.sauron.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CaptureEntity): Long

    @Query("SELECT * FROM captures ORDER BY detectedAt DESC")
    fun observeAll(): Flow<List<CaptureEntity>>

    @Query("SELECT COUNT(*) FROM captures")
    suspend fun count(): Int

    @Query("SELECT * FROM captures WHERE status = :status ORDER BY detectedAt")
    suspend fun pending(status: CaptureStatus = CaptureStatus.CAPTURED): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE status IN (:statuses) AND gemmaAttempts < :maxAttempts ORDER BY detectedAt")
    suspend fun gemmaPending(
        statuses: Collection<CaptureStatus> = listOf(CaptureStatus.NEEDS_GEMMA, CaptureStatus.NO_PARSE),
        maxAttempts: Int = CaptureStatus.MAX_GEMMA_ATTEMPTS,
    ): List<CaptureEntity>

    @Query("UPDATE captures SET status = :to WHERE id = :id AND status = :from")
    suspend fun claim(id: Long, to: CaptureStatus, from: CaptureStatus = CaptureStatus.CAPTURED): Int

    @Query("UPDATE captures SET status = :to WHERE id = :id AND status IN (:from)")
    suspend fun claimForGemma(
        id: Long,
        to: CaptureStatus,
        from: Collection<CaptureStatus> = listOf(CaptureStatus.NEEDS_GEMMA, CaptureStatus.NO_PARSE),
    ): Int

    @Query("UPDATE captures SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: CaptureStatus)

    @Query("UPDATE captures SET status = :to, gemmaAttempts = gemmaAttempts + 1 WHERE id = :id")
    suspend fun parkAfterGemma(id: Long, to: CaptureStatus = CaptureStatus.NO_PARSE)
}
