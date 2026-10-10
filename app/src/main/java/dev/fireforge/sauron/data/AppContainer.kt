package dev.fireforge.sauron.data

import android.content.Context
import androidx.room.Room

class AppContainer(context: Context) {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()
    }

    val expenseDao: ExpenseDao get() = database.expenseDao()

    val categoryDao: CategoryDao get() = database.categoryDao()
}
