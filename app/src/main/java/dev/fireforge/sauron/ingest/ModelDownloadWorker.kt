package dev.fireforge.sauron.ingest

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.fireforge.sauron.SauronApplication
import dev.fireforge.sauron.ai.GemmaSlipReader
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // A missing INTERNET permission surfaces as SocketException, which would retry forever.
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.INTERNET) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "download failed: INTERNET permission missing")
            return Result.failure()
        }
        try {
            setForeground(foregroundInfo())
        } catch (t: Throwable) {
            Log.w(TAG, "foreground promotion denied; continuing in background", t)
        }
        val reader = (applicationContext as SauronApplication).container.gemmaSlipReader
        val target = reader.modelTarget()
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        return try {
            val connection = URL(GemmaSlipReader.MODEL_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            try {
                when (connection.responseCode) {
                    in 400..499 -> {
                        tmp.delete()
                        return Result.failure()
                    }
                    in 500..599 -> {
                        tmp.delete()
                        return Result.retry()
                    }
                }
                val expected = connection.contentLengthLong
                var written = 0L
                var reported = -1
                connection.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (expected > 0) {
                                val percent = (written * 100 / expected).toInt()
                                if (percent > reported) {
                                    reported = percent
                                    setProgress(workDataOf(PROGRESS_PERCENT to percent))
                                }
                            }
                        }
                    }
                }
                if (expected > 0 && written != expected) {
                    tmp.delete()
                    Result.retry()
                } else if (tmp.renameTo(target)) {
                    Log.i(TAG, "model downloaded: $written bytes")
                    Result.success()
                } else {
                    tmp.delete()
                    Result.retry()
                }
            } finally {
                connection.disconnect()
            }
        } catch (t: IOException) {
            tmp.delete()
            Result.retry()
        }
    }

    private fun foregroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Model download", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading Gemma model")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        private const val TAG = "ModelDownload"
        const val UNIQUE_NAME = "gemma-model-download"
        const val PROGRESS_PERCENT = "percent"
        private const val CHANNEL_ID = "model-download"
        private const val NOTIFICATION_ID = 2
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER_BYTES = 1 shl 20
    }
}
