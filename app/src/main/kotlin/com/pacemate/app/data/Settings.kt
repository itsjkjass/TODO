package com.pacemate.app.data

import android.content.Context
import com.pacemate.core.AiProvider

/** Small key/value settings kept apart from the main state file. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("pacemate_settings", Context.MODE_PRIVATE)

    var provider: AiProvider
        get() = prefs.getString(KEY_PROVIDER, null)
            ?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() } ?: AiProvider.GROQ
        set(v) = prefs.edit().putString(KEY_PROVIDER, v.name).apply()

    /** Each provider keeps its own key and model, so switching back and forth loses nothing. */
    fun apiKey(p: AiProvider = provider): String = prefs.getString("api_key_${p.name}", "") ?: ""

    fun model(p: AiProvider = provider): String =
        prefs.getString("model_${p.name}", null)?.takeIf { it.isNotBlank() } ?: p.defaultModel

    fun saveAi(p: AiProvider, key: String, model: String) {
        prefs.edit()
            .putString(KEY_PROVIDER, p.name)
            .putString("api_key_${p.name}", key.trim())
            .putString("model_${p.name}", model.trim())
            .apply()
    }

    var startReminders: Boolean
        get() = prefs.getBoolean(KEY_REMINDERS, true)
        set(v) = prefs.edit().putBoolean(KEY_REMINDERS, v).apply()

    /** Request codes of reminder alarms currently scheduled, so they can be cancelled. */
    var scheduledReminderCodes: Set<String>
        get() = prefs.getStringSet(KEY_CODES, emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_CODES, v).apply()

    val hasApiKey: Boolean get() = apiKey().isNotBlank()

    private companion object {
        const val KEY_PROVIDER = "ai_provider"
        const val KEY_REMINDERS = "start_reminders"
        const val KEY_CODES = "reminder_codes"
    }
}
