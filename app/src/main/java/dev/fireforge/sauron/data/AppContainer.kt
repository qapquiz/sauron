package dev.fireforge.sauron.data

import android.content.Context
import androidx.room.Room
import dev.fireforge.sauron.ai.GemmaSlipReader

class AppContainer(context: Context) {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
            .build()
    }

    val expenseDao: ExpenseDao get() = database.expenseDao()

    val categoryDao: CategoryDao get() = database.categoryDao()

    val captureDao: CaptureDao get() = database.captureDao()

    val gemmaSlipReader: GemmaSlipReader by lazy { GemmaSlipReader(context) }
}
