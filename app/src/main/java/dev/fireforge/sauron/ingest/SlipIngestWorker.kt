package dev.fireforge.sauron.ingest

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.googlecode.tesseract.android.TessBaseAPI
import dev.fireforge.sauron.SauronApplication
import java.io.File
import dev.fireforge.sauron.data.CaptureEntity
import dev.fireforge.sauron.domain.SlipRules
import java.time.Instant

class SlipIngestWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!hasReadPermission()) return Result.failure()

        val container = (applicationContext as SauronApplication).container
        val prefs = applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val watermark = prefs.getLong(KEY_LAST_DATE_ADDED, 0L)

        val resolver = applicationContext.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
        try {
            var maxSeen = watermark
            resolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.DATE_ADDED} > ?",
                arrayOf(watermark.toString()),
                "${MediaStore.MediaColumns.DATE_ADDED} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED))
                    if (dateAdded > maxSeen) maxSeen = dateAdded
                    val uri = ContentUris.withAppendedId(collection, id)
                    val bitmap = decodeScaled(resolver, uri) ?: continue
                    val text = try {
                        recognize(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                    if (SlipRules.isSlip(SlipRules.score(text))) {
                        container.captureDao.insert(
                            CaptureEntity(
                                imageUri = uri.toString(),
                                takenAt = Instant.ofEpochSecond(dateAdded),
                                ocrText = text,
                                detectedAt = Instant.now(),
                            ),
                        )
                    }
                }
            }
            prefs.edit().putLong(KEY_LAST_DATE_ADDED, maxSeen).apply()
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private fun recognize(bitmap: Bitmap): String {
        ensureTessdata()
        val tess = TessBaseAPI()
        return try {
            tess.init(applicationContext.filesDir.absolutePath, TESS_LANGUAGE)
            tess.setImage(bitmap)
            tess.getUTF8Text() ?: ""
        } finally {
            tess.recycle()
        }
    }

    private fun ensureTessdata() {
        val target = File(applicationContext.filesDir, "tessdata")
        target.mkdirs()
        val names = applicationContext.assets.list("tessdata") ?: return
        for (name in names) {
            val outFile = File(target, name)
            if (outFile.exists()) continue
            applicationContext.assets.open("tessdata/$name").use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun hasReadPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(applicationContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun decodeScaled(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        if (maxDim <= 0) return null
        var sampleSize = 1
        while (maxDim / (sampleSize * 2) >= MAX_DIMENSION) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    companion object {
        private const val PREFS_FILE = "ingest"
        private const val KEY_LAST_DATE_ADDED = "last_date_added"
        private const val MAX_DIMENSION = 2048
        private const val TESS_LANGUAGE = "tha+eng"
    }
}
