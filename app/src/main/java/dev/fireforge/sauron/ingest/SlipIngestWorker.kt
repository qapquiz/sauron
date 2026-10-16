package dev.fireforge.sauron.ingest

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.googlecode.tesseract.android.TessBaseAPI
import dev.fireforge.sauron.SauronApplication
import androidx.room.withTransaction
import java.io.File
import dev.fireforge.sauron.data.AppContainer
import dev.fireforge.sauron.data.CaptureEntity
import dev.fireforge.sauron.data.CaptureStatus
import dev.fireforge.sauron.data.ExpenseEntity
import dev.fireforge.sauron.domain.Categorizer
import dev.fireforge.sauron.domain.Currency
import dev.fireforge.sauron.domain.SlipParse
import dev.fireforge.sauron.domain.SlipParser
import dev.fireforge.sauron.domain.SlipRules
import dev.fireforge.sauron.domain.SlipSource
import java.time.Instant

class SlipIngestWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!hasReadPermission()) return Result.failure()

        val container = (applicationContext as SauronApplication).container
        val prefs = applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAST_DATE_ADDED)) {
            // First run baseline: existing photos are never scanned, only new ones
            prefs.edit().putLong(KEY_LAST_DATE_ADDED, System.currentTimeMillis() / 1000).apply()
        }
        val watermark = prefs.getLong(KEY_LAST_DATE_ADDED, 0L)

        val resolver = applicationContext.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
        val (selection, selectionArgs) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "(${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DATE_ADDED} > ?)" to arrayOf("$WATCHED_FOLDER%", watermark.toString())
        } else {
            "(${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATE_ADDED} > ?)" to arrayOf("%/$WATCHED_FOLDER/%", watermark.toString())
        }
        try {
            var maxSeen = watermark
            var seen = 0
            var decoded = 0
            var slips = 0
            resolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_ADDED} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED))
                    if (dateAdded > maxSeen) maxSeen = dateAdded
                    val uri = ContentUris.withAppendedId(collection, id)
                    seen++
                    val bitmap = decodeScaled(resolver, uri)
                    if (bitmap == null) {
                        Log.w(TAG, "decode returned null for $uri")
                        continue
                    }
                    decoded++
                    val text = try {
                        recognize(preprocess(bitmap))
                    } finally {
                        bitmap.recycle()
                    }
                    val score = SlipRules.score(text)
                    Log.d(TAG, "ocr for $uri scored ${score.total}: $text")
                    val status = when {
                        SlipRules.isSlip(score) -> CaptureStatus.CAPTURED
                        score.total >= GEMMA_SCORE_MIN -> CaptureStatus.NEEDS_GEMMA
                        else -> null
                    }
                    if (status != null) {
                        slips++
                        container.captureDao.insert(
                            CaptureEntity(
                                imageUri = uri.toString(),
                                takenAt = Instant.ofEpochSecond(dateAdded),
                                ocrText = text,
                                detectedAt = Instant.now(),
                                status = status,
                            ),
                        )
                    }
                }
            }
            prefs.edit().putLong(KEY_LAST_DATE_ADDED, maxSeen).apply()
            Log.i(TAG, "pass done: seen=$seen decoded=$decoded slips=$slips watermark=$maxSeen")
            parsePendingCaptures(container)
            gemmaPendingCaptures(container)
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private suspend fun parsePendingCaptures(container: AppContainer) {
        val otherId = container.categoryDao.otherCategoryId()
        if (otherId == null) {
            Log.w(TAG, "no Other category; skipping parse stage")
            return
        }
        var parsed = 0
        var needsHelp = 0
        for (capture in container.captureDao.pending()) {
            if (container.captureDao.claim(capture.id, CaptureStatus.PARSING) != 1) continue
            when (val result = SlipParser.parse(capture.ocrText)) {
                is SlipParse.Parsed -> {
                    val categoryName = Categorizer.categorize(result.merchant) ?: "Other"
                    val categoryId = categoryName.let { container.categoryDao.findIdByName(it) } ?: otherId
                    container.database.withTransaction {
                        container.expenseDao.insert(
                            ExpenseEntity(
                                merchant = result.merchant,
                                amountMinor = result.amountMinor,
                                currency = Currency.THB,
                                categoryId = categoryId,
                                occurredAt = result.occurredAt,
                                bank = result.bank?.name,
                                source = SlipSource.ScreenCapture(capture.imageUri, capture.takenAt),
                                createdAt = Instant.now(),
                            ),
                        )
                        container.captureDao.updateStatus(capture.id, CaptureStatus.PARSED)
                    }
                    parsed++
                }
                is SlipParse.NeedsHelp -> {
                    container.captureDao.updateStatus(capture.id, CaptureStatus.NO_PARSE)
                    Log.w(TAG, "capture ${capture.id} needs help: ${result.reasons}")
                    needsHelp++
                }
            }
        }
        Log.i(TAG, "parse done: parsed=$parsed needsHelp=$needsHelp")
    }

    private suspend fun gemmaPendingCaptures(container: AppContainer) {
        val otherId = container.categoryDao.otherCategoryId()
        if (otherId == null) {
            Log.w(TAG, "no Other category; skipping gemma stage")
            return
        }
        var parsed = 0
        var needsHelp = 0
        for (capture in container.captureDao.gemmaPending()) {
            if (container.captureDao.claimForGemma(capture.id, CaptureStatus.PARSING) != 1) continue
            val bytes = readOriginalBytes(capture.imageUri)
            if (bytes == null) {
                // Parked rows return while gemmaAttempts < 3, so unreadable images retry up to MAX_GEMMA_ATTEMPTS times
                container.captureDao.parkAfterGemma(capture.id)
                Log.w(TAG, "gemma: image bytes unreadable for ${capture.imageUri}")
                needsHelp++
                continue
            }
            when (val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                container.gemmaSlipReader.readSlip(bytes)
            }) {
                is SlipParse.Parsed -> {
                    val categoryName = Categorizer.categorize(result.merchant) ?: "Other"
                    val categoryId = categoryName.let { container.categoryDao.findIdByName(it) } ?: otherId
                    container.database.withTransaction {
                        container.expenseDao.insert(
                            ExpenseEntity(
                                merchant = result.merchant,
                                amountMinor = result.amountMinor,
                                currency = Currency.THB,
                                categoryId = categoryId,
                                occurredAt = result.occurredAt,
                                bank = result.bank?.name,
                                source = SlipSource.ScreenCapture(capture.imageUri, capture.takenAt),
                                createdAt = Instant.now(),
                            ),
                        )
                        container.captureDao.updateStatus(capture.id, CaptureStatus.PARSED)
                    }
                    parsed++
                }
                is SlipParse.NeedsHelp -> {
                    container.captureDao.parkAfterGemma(capture.id)
                    Log.w(TAG, "capture ${capture.id} gemma needs help: ${result.reasons}")
                    needsHelp++
                }
            }
        }
        Log.i(TAG, "gemma done: parsed=$parsed needsHelp=$needsHelp")
    }

    private fun readOriginalBytes(imageUri: String): ByteArray? = try {
        applicationContext.contentResolver.openInputStream(Uri.parse(imageUri))?.use { it.readBytes() }
    } catch (t: Throwable) {
        Log.w(TAG, "openInputStream failed for $imageUri", t)
        null
    }

    private fun preprocess(bitmap: Bitmap): Bitmap {
        val gray = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(gray).drawBitmap(
            bitmap,
            0f,
            0f,
            Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) },
        )
        return gray
    }

    private fun recognize(bitmap: Bitmap): String {
        ensureTessdata()
        val tess = TessBaseAPI()
        return try {
            tess.init(applicationContext.filesDir.absolutePath, TESS_LANGUAGE)
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_COLUMN)
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
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(uri)
            if (boundsStream == null) {
                Log.w(TAG, "openInputStream null on bounds pass for $uri")
                return null
            }
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            if (maxDim <= 0) {
                Log.w(TAG, "bounds decode failed for $uri w=${bounds.outWidth} h=${bounds.outHeight}")
                return null
            }
            var sampleSize = 1
            while (maxDim / (sampleSize * 2) >= MAX_DIMENSION) sampleSize *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val dataStream = resolver.openInputStream(uri)
            if (dataStream == null) {
                Log.w(TAG, "openInputStream null on decode pass for $uri")
                return null
            }
            val bitmap = dataStream.use { BitmapFactory.decodeStream(it, null, options) }
            if (bitmap == null) Log.w(TAG, "decodeStream null for $uri")
            return bitmap
        } catch (t: Throwable) {
            Log.w(TAG, "decode threw for $uri", t)
            return null
        }
    }

    companion object {
        private const val TAG = "SlipIngest"
        private const val PREFS_FILE = "ingest"
        private const val KEY_LAST_DATE_ADDED = "last_date_added"
        private const val MAX_DIMENSION = 2048
        private const val TESS_LANGUAGE = "tha+eng"
        private const val GEMMA_SCORE_MIN = 2
        private const val MAX_GEMMA_ATTEMPTS = 3
        const val WATCHED_FOLDER = "Pictures/Screenshots"
    }
}
