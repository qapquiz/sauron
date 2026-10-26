package dev.fireforge.sauron.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dev.fireforge.sauron.domain.SlipParse
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class GemmaSlipReader(private val context: Context) {

    private var engine: Engine? = null

    fun readSlip(imageBytes: ByteArray): SlipParse {
        val e = ensureEngine() ?: return readViaGeminiApi(imageBytes)
        return try {
            e.createConversation().use { conversation ->
                val reply = conversation.sendMessage(
                    Contents.of(Content.ImageBytes(imageBytes), Content.Text(SLIP_PROMPT)),
                )
                val text = reply.contents.contents.filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                GemmaSlipJson.parse(text)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "gemma readSlip failed", t)
            SlipParse.NeedsHelp(listOf(REASON_GEMMA_FAILED))
        }
    }

    // Temporary fallback for phones where the engine cannot load. A missing or
    // empty key file keeps the pre-fallback behavior exactly.
    private fun readViaGeminiApi(imageBytes: ByteArray): SlipParse {
        val lines = runCatching { File(context.filesDir, KEY_FILE).readLines() }.getOrNull().orEmpty()
        val key = lines.firstOrNull()?.trim().orEmpty()
        if (key.isEmpty()) return SlipParse.NeedsHelp(listOf(REASON_GEMMA_FAILED))
        val model = lines.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: GEMINI_DEFAULT_MODEL
        Log.i(TAG, "gemma engine unavailable; falling back to gemini api model $model")
        return try {
            GemmaSlipJson.parse(geminiGenerate(key, model, imageBytes))
        } catch (t: Throwable) {
            Log.w(TAG, "gemini api readSlip failed", t)
            SlipParse.NeedsHelp(listOf(REASON_GEMMA_FAILED))
        }
    }

    private fun geminiGenerate(key: String, model: String, imageBytes: ByteArray): String {
        val body = JSONObject().put(
            "contents",
            JSONArray().put(
                JSONObject().put(
                    "parts",
                    JSONArray()
                        .put(
                            JSONObject().put(
                                "inline_data",
                                JSONObject()
                                    .put("mime_type", "image/jpeg")
                                    .put("data", Base64.encodeToString(imageBytes, Base64.NO_WRAP)),
                            ),
                        )
                        .put(JSONObject().put("text", SLIP_PROMPT)),
                ),
            ),
        )
        val conn = URL("$GEMINI_API_BASE/models/$model:generateContent").openConnection() as HttpURLConnection
        val reply = try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("x-goog-api-key", key)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = GEMINI_CONNECT_TIMEOUT_MS
            conn.readTimeout = GEMINI_READ_TIMEOUT_MS
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("gemini api http $code")
            conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
        val parts = JSONObject(reply)
            .optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("gemini api reply has no candidate parts")
        return (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text").orEmpty() }
    }

    @Synchronized
    private fun ensureEngine(): Engine? {
        engine?.let { return it }
        for (candidate in modelCandidates().filter { it.exists() }) {
            val useGpu = candidate.name == GPU_FILE
            val backend = if (useGpu) Backend.GPU() else Backend.CPU()
            try {
                Engine(
                    EngineConfig(
                        modelPath = candidate.absolutePath,
                        backend = backend,
                        visionBackend = backend,
                    ),
                ).also {
                    it.initialize()
                    engine = it
                    Log.i(TAG, "gemma engine loaded from ${candidate.name}")
                    return it
                }
            } catch (t: Throwable) {
                Log.w(TAG, "gemma engine load failed for ${candidate.name}", t)
            }
        }
        Log.w(TAG, "no gemma model loaded; tried ${modelCandidates().joinToString { it.absolutePath }}")
        return null
    }

    fun hasModel(): Boolean = modelCandidates().any { it.exists() && it.length() > MIN_MODEL_BYTES }

    fun modelTarget(): File = File(context.filesDir, "models/$GPU_FILE")

    private fun modelCandidates(): List<File> {
        val external = context.getExternalFilesDir(null)?.let { File(it, "models") }
        return listOfNotNull(external?.let { File(it, GPU_FILE) }, File(context.filesDir, "models/$GPU_FILE"))
            .plus(listOfNotNull(external?.let { File(it, CPU_FILE) }, File(context.filesDir, "models/$CPU_FILE")))
    }

    companion object {
        private const val TAG = "Gemma"
        private const val GPU_FILE = "gemma-4-E2B-it-gpu.litertlm"
        private const val CPU_FILE = "gemma-4-E2B-it.litertlm"
        private const val MIN_MODEL_BYTES = 1_000_000_000L
        const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-gpu.litertlm"
        private const val REASON_GEMMA_FAILED = "gemma_failed"
        private const val KEY_FILE = "gemini.txt"
        private const val GEMINI_DEFAULT_MODEL = "gemma-3-27b-it"
        private const val GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta"
        private const val GEMINI_CONNECT_TIMEOUT_MS = 15_000
        private const val GEMINI_READ_TIMEOUT_MS = 120_000

        private val SLIP_PROMPT = """
            You are reading a Thai bank payment slip image. Reply with ONLY minified JSON, no markdown fences, no other text.
            Shape: {"amount": "49.00", "merchant": "...", "date": "YYYY-MM-DD", "time": "HH:MM", "bank": "SCB|KBANK|BBL|KTB|BAY|TTB|GSB|CLICX" or null, "category": "Food & Drink|Transport|Shopping|Bills|Income|Other"}.
            "amount" is the จำนวนเงิน or จำนวน value as a decimal string of baht, for example "49.00".
            Thai month abbreviations map to months: ม.ค. = 01, ก.พ. = 02, มี.ค. = 03, เม.ย. = 04, พ.ค. = 05, มิ.ย. = 06, ก.ค. = 07, ส.ค. = 08, ก.ย. = 09, ต.ค. = 10, พ.ย. = 11, ธ.ค. = 12.
            A 2-digit year is Buddhist Era: 69 = 2569 = CE 2026.
            If the shop is a common chain such as แมคโดนัลด์, KFC or 7-Eleven, write its standard Thai name.
            Use null for any field the slip does not show.
        """.trimIndent()
    }
}
