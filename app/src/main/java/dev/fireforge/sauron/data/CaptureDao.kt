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
}
