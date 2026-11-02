package com.pacemate.app.data

import android.content.Context
import com.pacemate.core.AppState
import com.pacemate.core.PaceJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Single source of truth for the app's data, stored as one JSON file in
 * private app storage. Every change goes through [update] so the UI,
 * notification actions and alarms always see the same state.
 */
class Repository(context: Context) {
    private val file = File(context.filesDir, "pacemate_state.json")
    private val lock = Any()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    val current: AppState get() = _state.value

    fun update(transform: (AppState) -> AppState): AppState = synchronized(lock) {
        val next = transform(_state.value)
        if (next != _state.value) {
            _state.value = next
            save(next)
        }
        next
    }

    private fun load(): AppState = try {
        if (file.exists()) PaceJson.decodeFromString(AppState.serializer(), file.readText()) else AppState()
    } catch (e: Exception) {
        // Corrupt file: keep a copy for inspection and start fresh rather than crash.
        runCatching { file.copyTo(File(file.parentFile, "pacemate_state.broken.json"), overwrite = true) }
        AppState()
    }

    private fun save(state: AppState) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(PaceJson.encodeToString(AppState.serializer(), state))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
