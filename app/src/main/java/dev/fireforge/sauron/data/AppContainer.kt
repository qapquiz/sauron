package dev.fireforge.sauron.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import dev.fireforge.sauron.ai.GemmaSlipReader
import dev.fireforge.sauron.ingest.IngestPipeline
import dev.fireforge.sauron.ingest.MediaStoreScanner
import dev.fireforge.sauron.ingest.TessOcr

class AppContainer(context: Context) {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
    }

    val expenseDao: ExpenseDao get() = database.expenseDao()

    val categoryDao: CategoryDao get() = database.categoryDao()

    val captureDao: CaptureDao get() = database.captureDao()

    val expenseFiler: ExpenseFiler by lazy {
        ExpenseFiler(expenseDao, captureDao, categoryDao, Tx { block -> database.withTransaction { block() } })
    }

    val gemmaSlipReader: GemmaSlipReader by lazy { GemmaSlipReader(context) }

    val ingestPipeline: IngestPipeline by lazy {
        IngestPipeline(
            source = MediaStoreScanner(context.contentResolver),
            ocr = TessOcr(context, MediaStoreScanner(context.contentResolver)),
            gemma = gemmaSlipReader,
            captures = captureDao,
            filer = expenseFiler,
            otherCategoryId = { categoryDao.otherCategoryId() },
        )
    }
}
