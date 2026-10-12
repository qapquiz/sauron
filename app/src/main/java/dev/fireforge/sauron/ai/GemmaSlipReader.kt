package dev.fireforge.sauron.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dev.fireforge.sauron.domain.SlipParse
import java.io.File

class GemmaSlipReader(private val context: Context) {

    private var engine: Engine? = null

    fun readSlip(imageBytes: ByteArray): SlipParse {
        val e = ensureEngine() ?: return SlipParse.NeedsHelp(listOf(REASON_GEMMA_FAILED))
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
        return null
    }

    // adb push target for development: /sdcard/Android/data/dev.fireforge.sauron/files/models/
    private fun modelCandidates(): List<File> {
        val external = context.getExternalFilesDir(null)?.let { File(it, "models") }
        return listOfNotNull(external?.let { File(it, GPU_FILE) }, File(context.filesDir, "models/$GPU_FILE"))
            .plus(listOfNotNull(external?.let { File(it, CPU_FILE) }, File(context.filesDir, "models/$CPU_FILE")))
    }

    companion object {
        private const val TAG = "Gemma"
        private const val GPU_FILE = "gemma-4-E2B-it-gpu.litertlm"
        private const val CPU_FILE = "gemma-4-E2B-it.litertlm"
        private const val REASON_GEMMA_FAILED = "gemma_failed"

        private val SLIP_PROMPT = """
            You are reading a Thai bank payment slip image. Reply with ONLY minified JSON, no markdown fences, no other text.
            Shape: {"amount": "49.00", "merchant": "...", "date": "YYYY-MM-DD", "time": "HH:MM", "bank": "SCB|KBANK|BBL|KTB|BAY|TTB|GSB" or null, "category": "Food & Drink|Transport|Shopping|Bills|Income|Other"}.
            "amount" is the จำนวนเงิน or จำนวน value as a decimal string of baht, for example "49.00".
            Thai month abbreviations map to months: ม.ค. = 01, ก.พ. = 02, มี.ค. = 03, เม.ย. = 04, พ.ค. = 05, มิ.ย. = 06, ก.ค. = 07, ส.ค. = 08, ก.ย. = 09, ต.ค. = 10, พ.ย. = 11, ธ.ค. = 12.
            A 2-digit year is Buddhist Era: 69 = 2569 = CE 2026.
            Use null for any field the slip does not show.
        """.trimIndent()
    }
}
