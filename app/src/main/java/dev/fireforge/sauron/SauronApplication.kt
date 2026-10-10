package dev.fireforge.sauron

import android.app.Application
import dev.fireforge.sauron.data.AppContainer
import dev.fireforge.sauron.data.SeedCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    }
}
