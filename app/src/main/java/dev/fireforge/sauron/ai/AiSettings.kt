package dev.fireforge.sauron.ai

import android.content.Context
import android.content.SharedPreferences

class AiSettings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }
        set(value) = prefs.edit().putString(KEY_API_KEY, value?.trim()).apply()

    var modelId: String
        get() = prefs.getString(KEY_MODEL_ID, null)?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MODEL_ID
        set(value) = prefs.edit().putString(KEY_MODEL_ID, value.trim()).apply()

    companion object {
        const val DEFAULT_MODEL_ID = "gemma-3-27b-it"
        private const val PREFS_NAME = "ai"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL_ID = "model_id"
    }
}
