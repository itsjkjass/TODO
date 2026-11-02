package com.pacemate.app.data

import android.content.Context
import com.pacemate.core.ClaudePlanner

/** Small key/value settings kept apart from the main state file. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("pacemate_settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, "") ?: ""
        set(v) = prefs.edit().putString(KEY_API, v.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: ClaudePlanner.DEFAULT_MODEL
        set(v) = prefs.edit().putString(KEY_MODEL, v.trim()).apply()

    var startReminders: Boolean
        get() = prefs.getBoolean(KEY_REMINDERS, true)
        set(v) = prefs.edit().putBoolean(KEY_REMINDERS, v).apply()

    /** Request codes of reminder alarms currently scheduled, so they can be cancelled. */
    var scheduledReminderCodes: Set<String>
        get() = prefs.getStringSet(KEY_CODES, emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_CODES, v).apply()

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    private companion object {
        const val KEY_API = "anthropic_api_key"
        const val KEY_MODEL = "model"
        const val KEY_REMINDERS = "start_reminders"
        const val KEY_CODES = "reminder_codes"
    }
}
