package dev.fireforge.sauron.ingest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/** Reads the image at a uri into text, or null when it cannot be decoded. */
interface OcrReader {
    fun recognize(uri: String): String?
}

class TessOcr(
    private val context: Context,
    private val source: ImageSource,
) : OcrReader {

    override fun recognize(uri: String): String? {
        val bitmap = source.decode(uri) ?: return null
        ensureTessdata()
        val gray = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(gray).drawBitmap(
            bitmap,
            0f,
            0f,
            Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) },
        )
        val tess = TessBaseAPI()
        return try {
            tess.init(context.filesDir.absolutePath, TESS_LANGUAGE)
            tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_COLUMN)
            tess.setImage(gray)
            tess.getUTF8Text() ?: ""
        } finally {
            tess.recycle()
            gray.recycle()
            bitmap.recycle()
        }
    }

    private fun ensureTessdata() {
        val target = File(context.filesDir, "tessdata")
        target.mkdirs()
        val names = context.assets.list("tessdata") ?: return
        for (name in names) {
            val outFile = File(target, name)
            if (outFile.exists()) continue
            context.assets.open("tessdata/$name").use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private companion object {
        const val TESS_LANGUAGE = "tha+eng"
    }
}
