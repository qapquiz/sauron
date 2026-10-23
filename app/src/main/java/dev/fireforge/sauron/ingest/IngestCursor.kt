package dev.fireforge.sauron.ingest

import android.content.Context

/** The only owner of the ingest watermark. */
class IngestCursor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    /** Reads the watermark, taking the first-run baseline: existing photos are never scanned. */
    fun read(): Long {
        if (!prefs.contains(KEY)) {
            prefs.edit().putLong(KEY, System.currentTimeMillis() / 1000).apply()
        }
        return prefs.getLong(KEY, 0L)
    }

    fun advance(to: Long) = prefs.edit().putLong(KEY, to).apply()

    private companion object {
        const val PREFS_FILE = "ingest"
        const val KEY = "last_date_added"
    }
}
