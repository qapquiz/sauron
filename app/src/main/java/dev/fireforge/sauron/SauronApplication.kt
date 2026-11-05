package dev.fireforge.sauron

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.fireforge.sauron.data.AppContainer
import dev.fireforge.sauron.data.SeedCategories
import dev.fireforge.sauron.ingest.SlipIngestWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SauronApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.categoryDao.insertAll(SeedCategories)
        }
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniqueWork(
            SlipIngestWorker.UNIQUE_ONE_TIME_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SlipIngestWorker>().build(),
        )
        workManager.enqueueUniquePeriodicWork(
            SlipIngestWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SlipIngestWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}
