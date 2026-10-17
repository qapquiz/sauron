package dev.fireforge.sauron.ingest

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Source of watched-folder images and of their decodable bytes. The one MediaStore caller. */
interface ImageSource {
    fun query(watermark: Long): List<MediaStoreScanner.ScannedImage>
    fun decode(uri: String): Bitmap?
    fun readOriginalBytes(uri: String): ByteArray?
}

class MediaStoreScanner(private val resolver: ContentResolver) : ImageSource {

    data class ScannedImage(val uri: String, val dateAdded: Long)

    override fun query(watermark: Long): List<ScannedImage> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
        val (selection, selectionArgs) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "(${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DATE_ADDED} > ?)" to
                arrayOf("$WATCHED_FOLDER%", watermark.toString())
        } else {
            "(${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.DATE_ADDED} > ?)" to
                arrayOf("%/$WATCHED_FOLDER/%", watermark.toString())
        }
        val found = mutableListOf<ScannedImage>()
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
                found += ScannedImage(ContentUris.withAppendedId(collection, id).toString(), dateAdded)
            }
        }
        android.util.Log.d("SlipIngest", "DEBUG query wm=$watermark sel=$selection args=${selectionArgs.contentToString()} found=${found.size}")
        return found
    }

    override fun decode(uri: String): Bitmap? {
        val parsed = Uri.parse(uri)
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: run { RunLog.decodeFailed(parsed, "bounds stream null"); return null }
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            if (maxDim <= 0) {
                RunLog.decodeFailed(parsed, "bounds w=${bounds.outWidth} h=${bounds.outHeight}")
                return null
            }
            var sampleSize = 1
            while (maxDim / (sampleSize * 2) >= MAX_DIMENSION) sampleSize *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = resolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (bitmap == null) RunLog.decodeFailed(parsed, "decode stream null")
            bitmap
        } catch (t: Throwable) {
            RunLog.decodeFailed(parsed, "threw ${t.javaClass.simpleName}")
            null
        }
    }

    override fun readOriginalBytes(uri: String): ByteArray? = try {
        resolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
    } catch (t: Throwable) {
        RunLog.unreadableImage(uri, "openInputStream")
        null
    }

    companion object {
        private const val MAX_DIMENSION = 2048
        const val WATCHED_FOLDER = "Pictures/Screenshots"
    }
}
