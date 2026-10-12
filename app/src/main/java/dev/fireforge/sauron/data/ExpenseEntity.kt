package dev.fireforge.sauron.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverters
import dev.fireforge.sauron.domain.Currency
import dev.fireforge.sauron.domain.SlipSource
import java.time.Instant

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val amountMinor: Long,
    val currency: Currency,
    val categoryId: Long,
    val occurredAt: Instant,
    val source: SlipSource,
    val createdAt: Instant,
    val bank: String? = null,
)

data class ExpenseWithCategory(
    @Embedded val expense: ExpenseEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id") val category: CategoryEntity,
)

data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val totalMinor: Long,
)
