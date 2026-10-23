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
        val cursor = container.ingestCursor
        return try {
            val report = container.ingestPipeline.run(cursor.read())
            cursor.advance(report.maxSeen)
            RunLog.passDone(report.seen, report.decoded, report.slips, report.maxSeen)
            Result.success()
        } catch (e: Exception) {
            RunLog.workerRetry(e)
            Result.retry()
        }
    }
}
