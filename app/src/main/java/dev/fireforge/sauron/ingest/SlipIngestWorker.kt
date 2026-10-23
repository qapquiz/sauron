package dev.fireforge.sauron.ingest

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.fireforge.sauron.SauronApplication

class SlipIngestWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SauronApplication).container
        if (!ImagePermission.granted(applicationContext)) {
            RunLog.permissionDenied()
            return Result.failure()
        }
        val prefs = applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAST_DATE_ADDED)) {
            // First run baseline: existing photos are never scanned, only new ones
            prefs.edit().putLong(KEY_LAST_DATE_ADDED, System.currentTimeMillis() / 1000).apply()
        }
        return try {
            val report = container.ingestPipeline.run(prefs.getLong(KEY_LAST_DATE_ADDED, 0L))
            prefs.edit().putLong(KEY_LAST_DATE_ADDED, report.maxSeen).apply()
            RunLog.passDone(report.seen, report.decoded, report.slips, report.maxSeen)
            Result.success()
        } catch (e: Exception) {
            RunLog.workerRetry(e)
            Result.retry()
        }
    }

    private companion object {
        const val PREFS_FILE = "ingest"
        const val KEY_LAST_DATE_ADDED = "last_date_added"
    }
}
