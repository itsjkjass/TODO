package com.pacemate.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pacemate.app.PaceMateApp
import com.pacemate.app.timer.TimerController
import com.pacemate.core.AiException
import com.pacemate.core.AppState
import com.pacemate.core.BreakdownResult
import com.pacemate.core.AiPlanner
import com.pacemate.core.AiProvider
import com.pacemate.core.Goal
import com.pacemate.core.GoalRequest
import com.pacemate.core.OfflineBreakdowner
import com.pacemate.core.PaceModel
import com.pacemate.core.StateOps
import com.pacemate.core.UserProfile
import com.pacemate.core.merge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PaceMateApp
    private val repo = app.repository
    val settings = app.settings

    val state: StateFlow<AppState> = repo.state

    private val _busy = MutableStateFlow<String?>(null)
    /** Non-null while an AI request is running; the text describes what's happening. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    /** One-off messages shown in a snackbar. */
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _apiKeySet = MutableStateFlow(settings.hasApiKey)
    val apiKeySet: StateFlow<Boolean> = _apiKeySet.asStateFlow()

    fun consumeMessage() { _message.value = null }
    fun toast(text: String) { _message.value = text }

    private fun planner(): AiPlanner? =
        if (settings.hasApiKey) AiPlanner(settings.provider, settings.apiKey(), settings.model()) else null

    // ---------- settings ----------

    fun saveAi(provider: AiProvider, key: String, model: String) {
        settings.saveAi(provider, key, model)
        _apiKeySet.value = settings.hasApiKey
    }

    fun setReminders(enabled: Boolean) {
        settings.startReminders = enabled
        TimerController.rescheduleReminders(app)
    }

    // ---------- onboarding / profile ----------

    fun updateProfile(transform: (UserProfile) -> UserProfile) {
        repo.update { it.copy(profile = transform(it.profile)) }
    }

    /** Uses the AI to read a free-text description of the user's day into routine fields. */
    fun fillRoutineFromText(text: String) {
        val p = planner() ?: run { toast("Add a Groq or Kimi API key first (Settings), or fill the fields below by hand."); return }
        if (text.isBlank()) { toast("Describe your typical day first."); return }
        viewModelScope.launch {
            _busy.value = "Reading your routine…"
            try {
                val draft = p.parseRoutine(text, repo.current.profile)
                repo.update { s ->
                    s.copy(profile = s.profile.merge(draft).let { pr ->
                        pr.copy(preferences = pr.preferences.copy(notes = mergeNotes(pr.preferences.notes, text)))
                    })
                }
                toast(draft.summary.ifBlank { "Routine filled in. Please check the details." })
            } catch (e: AiException) {
                toast(e.message ?: "AI request failed")
            } catch (e: Exception) {
                toast("AI request failed: ${e.message}")
            } finally {
                _busy.value = null
            }
        }
    }

    private fun mergeNotes(existing: String, text: String): String =
        if (existing.isBlank()) text.take(1000) else existing

    /** Until the logger has real data, the pace comes from the user's self-rating. */
    private fun applySelfRatedPace() {
        repo.update { s ->
            if (s.pace.samples == 0) s.copy(pace = PaceModel.initialStats(s.profile.preferences.selfRatedPace)) else s
        }
    }

    /** Builds a plan to preview at the end of onboarding. */
    fun previewPlan() {
        applySelfRatedPace()
        replan()
    }

    fun finishOnboarding() {
        applySelfRatedPace()
        repo.update { it.copy(onboarded = true) }
        replan()
    }

    // ---------- goals ----------

    /** Breaks a goal into steps (AI if available, otherwise offline) and returns the draft for review. */
    fun breakdownGoal(request: GoalRequest, onResult: (BreakdownResult) -> Unit) {
        viewModelScope.launch {
            _busy.value = "Breaking \"${request.title}\" into steps…"
            val s = repo.current
            val result = try {
                val p = planner()
                if (p != null) p.breakdown(request, s.profile, s.pace, LocalDate.now().toString())
                else OfflineBreakdowner.breakdown(request, s.profile, s.pace, LocalDate.now().toString())
            } catch (e: Exception) {
                toast((e.message ?: "AI request failed") + " Using the offline breakdown instead.")
                OfflineBreakdowner.breakdown(request, s.profile, s.pace, LocalDate.now().toString())
            } finally {
                _busy.value = null
            }
            onResult(result)
        }
    }

    fun saveGoal(goal: Goal) {
        repo.update { StateOps.upsertGoal(it, goal) }
        replan()
    }

    fun deleteGoal(goalId: String) {
        val wasActive = repo.current.activeTimer?.let { t -> repo.current.sessions.any { it.id == t.sessionId && it.goalId == goalId } } == true
        if (wasActive) TimerController.stop(app, markDone = false)
        repo.update { StateOps.deleteGoal(it, goalId) }
        replan()
    }

    fun completeSubtask(goalId: String, subtaskId: String) {
        val active = repo.current.activeTimer
        if (active != null && repo.current.sessions.any { it.id == active.sessionId && it.subtaskId == subtaskId }) {
            TimerController.stop(app, markDone = true)
        }
        repo.update { StateOps.completeSubtask(it, goalId, subtaskId) }
        replan()
    }

    fun reopenSubtask(goalId: String, subtaskId: String) {
        repo.update { StateOps.reopenSubtask(it, goalId, subtaskId) }
        replan()
    }

    // ---------- plan ----------

    fun replan(showMessage: Boolean = false) {
        applySelfRatedPace()
        val now = LocalTime.now()
        repo.update { StateOps.replan(it, LocalDate.now(), now.hour * 60 + now.minute) }
        TimerController.rescheduleReminders(app)
        if (showMessage) {
            val w = repo.current.planWarnings
            toast(if (w.isEmpty()) "Plan updated. Everything fits comfortably." else "Plan updated with ${w.size} heads-up(s).")
        }
    }

    // ---------- timer ----------

    fun startTimer(sessionId: String) = TimerController.start(app, sessionId)
    fun stopTimer(markDone: Boolean) = TimerController.stop(app, markDone)
    fun extendTimer() = TimerController.extend(app, 10)
    fun markDone(sessionId: String) = TimerController.markDone(app, sessionId)

    fun skipSession(sessionId: String) {
        if (repo.current.activeTimer?.sessionId == sessionId) TimerController.stop(app, markDone = false)
        repo.update { StateOps.skipSession(it, sessionId) }
        replan()
    }

    fun resetEverything() {
        TimerController.stop(app, markDone = false)
        repo.update { AppState() }
        TimerController.rescheduleReminders(app)
    }

    init {
        // Make sure a running timer still has its alarm + notification (e.g. after the app was killed).
        TimerController.sync(app)
    }
}
