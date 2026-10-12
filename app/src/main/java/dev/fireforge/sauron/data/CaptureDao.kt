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

    @Query("SELECT * FROM captures WHERE status = 'CAPTURED' ORDER BY detectedAt")
    suspend fun pending(): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE status IN ('NEEDS_GEMMA', 'NO_PARSE') AND gemmaAttempts < 3 ORDER BY detectedAt")
    suspend fun gemmaPending(): List<CaptureEntity>

    @Query("UPDATE captures SET status = :status WHERE id = :id AND status = 'CAPTURED'")
    suspend fun claim(id: Long, status: CaptureStatus): Int

    @Query("UPDATE captures SET status = :status WHERE id = :id AND status IN ('NEEDS_GEMMA', 'NO_PARSE')")
    suspend fun claimForGemma(id: Long, status: CaptureStatus): Int

    @Query("UPDATE captures SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: CaptureStatus)

    @Query("UPDATE captures SET status = 'NO_PARSE', gemmaAttempts = gemmaAttempts + 1 WHERE id = :id")
    suspend fun parkAfterGemma(id: Long)
}
