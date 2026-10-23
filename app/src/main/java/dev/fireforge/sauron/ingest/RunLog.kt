package dev.fireforge.sauron.ingest

import android.util.Log

/** The only Log call site in the ingest path. Carries counts, ids, and uris; never slip text. */
object RunLog {
    private const val TAG = "SlipIngest"

    fun passDone(seen: Int, decoded: Int, slips: Int, watermark: Long) =
        Log.i(TAG, "pass done: seen=$seen decoded=$decoded slips=$slips watermark=$watermark")

    fun parseDone(parsed: Int, needsHelp: Int) =
        Log.i(TAG, "parse done: parsed=$parsed needsHelp=$needsHelp")

    fun gemmaDone(parsed: Int, needsHelp: Int) =
        Log.i(TAG, "gemma done: parsed=$parsed needsHelp=$needsHelp")

    fun captureNeedsHelp(id: Long, stage: String, reasons: List<String>) =
        Log.w(TAG, "capture $id $stage needs help: $reasons")

    fun unreadableImage(uri: String, stage: String) =
        Log.w(TAG, "$stage: image bytes unreadable for $uri")

    fun decodeFailed(uri: android.net.Uri, branch: String) =
        Log.w(TAG, "decode $branch for $uri")

    fun workerRetry(cause: Throwable) = Log.w(TAG, "ingest pass failed; retrying", cause)

    fun permissionDenied() = Log.w(TAG, "read permission missing; ingest cannot run")
}
