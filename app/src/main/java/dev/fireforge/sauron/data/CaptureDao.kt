package dev.fireforge.sauron.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CaptureEntity): Long

    @Query("SELECT * FROM captures ORDER BY detectedAt DESC")
    fun observeAll(): Flow<List<CaptureEntity>>

    @Query("SELECT COUNT(*) FROM captures")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM captures WHERE status != 'PARSED'")
    fun observeParkedCount(): Flow<Int>

    @Query("SELECT * FROM captures WHERE status = 'CAPTURED' ORDER BY detectedAt")
    suspend fun pending(): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE status IN ('NEEDS_GEMMA', 'NO_PARSE') AND gemmaAttempts < 3 ORDER BY detectedAt")
    suspend fun gemmaPending(): List<CaptureEntity>

    @Query("UPDATE captures SET status = :status, claimedAt = :claimedAt WHERE id = :id AND status = 'CAPTURED'")
    suspend fun claim(id: Long, status: CaptureStatus, claimedAt: Instant): Int

    @Query("UPDATE captures SET status = :status, claimedAt = :claimedAt WHERE id = :id AND status IN ('NEEDS_GEMMA', 'NO_PARSE')")
    suspend fun claimForGemma(id: Long, status: CaptureStatus, claimedAt: Instant): Int

    @Query("UPDATE captures SET status = :status, claimedAt = NULL WHERE id = :id")
    suspend fun updateStatus(id: Long, status: CaptureStatus)

    @Query("UPDATE captures SET status = 'NO_PARSE', claimedAt = NULL, gemmaAttempts = gemmaAttempts + 1 WHERE id = :id")
    suspend fun recordGemmaFailure(id: Long)

    @Query("UPDATE captures SET status = 'CAPTURED', claimedAt = NULL WHERE status = 'PARSING' AND (claimedAt IS NULL OR claimedAt < :cutoff)")
    suspend fun reopenStaleClaims(cutoff: Instant): Int
}
