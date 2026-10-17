package dev.fireforge.sauron.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class CaptureStatus {
    CAPTURED,
    NEEDS_GEMMA,
    PARSING,
    PARSED,
    NO_PARSE,
    ;

    companion object {
        const val MAX_GEMMA_ATTEMPTS = 3
    }
}

@Entity(
    tableName = "captures",
    indices = [Index(value = ["imageUri"], unique = true)],
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String,
    val takenAt: Instant,
    val ocrText: String,
    val detectedAt: Instant,
    val status: CaptureStatus = CaptureStatus.CAPTURED,
    val gemmaAttempts: Int = 0,
)
