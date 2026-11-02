package com.pacemate.core

import java.time.LocalDate
import java.util.UUID

/**
 * Pure state transitions used by the app. Keeping them here (instead of in
 * Android code) makes the timer/logger/planning rules unit-testable.
 */
object StateOps {

    private fun newId() = UUID.randomUUID().toString()

    // ---------- planning ----------

    fun replan(
        state: AppState,
        today: LocalDate,
        nowMinute: Int,
        scheduler: Scheduler = Scheduler(),
    ): AppState {
        val activeId = state.activeTimer?.sessionId
        val todayStr = today.toString()
        val keep = state.sessions.filter {
            it.status != SessionStatus.PLANNED || it.id == activeId || it.date < todayStr
        }
        val result = scheduler.plan(state.profile, state.goals, state.pace, today, nowMinute, keep)
        return state.copy(
            sessions = result.sessions,
            planWarnings = result.warnings,
            lastPlannedEpochMs = System.currentTimeMillis(),
        )
    }

    fun upsertGoal(state: AppState, goal: Goal): AppState {
        val exists = state.goals.any { it.id == goal.id }
        val goals = if (exists) state.goals.map { if (it.id == goal.id) goal else it } else state.goals + goal
        // Planned (not yet started) sessions of an edited goal are rebuilt by the next replan.
        val sessions = state.sessions.filterNot {
            it.goalId == goal.id && it.status == SessionStatus.PLANNED && it.id != state.activeTimer?.sessionId
        }
        return state.copy(goals = goals, sessions = sessions)
    }

    fun deleteGoal(state: AppState, goalId: String): AppState {
        val timer = state.activeTimer?.takeIf { t -> state.sessions.none { it.id == t.sessionId && it.goalId == goalId } }
        return state.copy(
            goals = state.goals.filterNot { it.id == goalId },
            sessions = state.sessions.filterNot { it.goalId == goalId && it.status == SessionStatus.PLANNED },
            planWarnings = state.planWarnings.filterNot { it.goalId == goalId },
            activeTimer = timer,
        )
    }

    // ---------- timer / logger ----------

    /** Starts the timer for a session; any running timer is stopped (and logged) first. */
    fun startTimer(state: AppState, sessionId: String, nowMs: Long): AppState {
        var s = state
        if (s.activeTimer != null) {
            if (s.activeTimer!!.sessionId == sessionId) return s
            s = stopTimer(s, nowMs, markDone = false)
        }
        val session = s.sessions.firstOrNull { it.id == sessionId } ?: return s
        val remainingSec = (session.durationMinutes * 60L - session.loggedSeconds).coerceAtLeast(60)
        return s.copy(activeTimer = ActiveTimer(sessionId, nowMs, nowMs + remainingSec * 1000))
    }

    /** Adds more time to the running session (e.g. "+10 min" from the notification). */
    fun extendTimer(state: AppState, minutes: Int, nowMs: Long): AppState {
        val t = state.activeTimer ?: return state
        val base = maxOf(t.plannedEndEpochMs, nowMs)
        return state.copy(activeTimer = t.copy(plannedEndEpochMs = base + minutes * 60_000L, extendedTimes = t.extendedTimes + 1))
    }

    /** Stops the running timer and writes a log entry. Optionally marks the session as done. */
    fun stopTimer(state: AppState, nowMs: Long, markDone: Boolean): AppState {
        val t = state.activeTimer ?: return state
        val session = state.sessions.firstOrNull { it.id == t.sessionId }
            ?: return state.copy(activeTimer = null)
        val goal = state.goals.firstOrNull { it.id == session.goalId }
        val elapsedSec = ((nowMs - t.startedAtEpochMs) / 1000).coerceAtLeast(0)
        val log = TimeLog(
            id = newId(),
            sessionId = session.id,
            goalId = session.goalId,
            subtaskId = session.subtaskId,
            title = session.title,
            category = goal?.category ?: "general",
            startEpochMs = t.startedAtEpochMs,
            endEpochMs = nowMs,
            plannedMinutes = session.durationMinutes,
        )
        val updated = state.copy(
            activeTimer = null,
            logs = if (elapsedSec >= 5) state.logs + log else state.logs,
            sessions = state.sessions.map {
                if (it.id == session.id) it.copy(loggedSeconds = it.loggedSeconds + elapsedSec) else it
            },
        )
        return if (markDone) markSessionDone(updated, session.id) else updated
    }

    /**
     * Marks a session done, teaches the pace model how long it really took and
     * completes the subtask / goal when nothing is left.
     */
    fun markSessionDone(state: AppState, sessionId: String): AppState {
        var s = state
        if (s.activeTimer?.sessionId == sessionId) return stopTimer(s, System.currentTimeMillis(), markDone = true)
        val session = s.sessions.firstOrNull { it.id == sessionId } ?: return s
        if (session.status == SessionStatus.DONE) return s
        val goal = s.goals.firstOrNull { it.id == session.goalId }

        val pace = if (session.loggedSeconds > 0) {
            PaceModel.learn(s.pace, goal?.category ?: "general", session.durationMinutes, session.loggedSeconds)
        } else s.pace

        s = s.copy(
            pace = pace,
            sessions = s.sessions.map { if (it.id == sessionId) it.copy(status = SessionStatus.DONE) else it },
        )
        val morePlanned = s.sessions.any { it.subtaskId == session.subtaskId && it.status == SessionStatus.PLANNED }
        if (!morePlanned) s = completeSubtask(s, session.goalId, session.subtaskId)
        return s
    }

    fun skipSession(state: AppState, sessionId: String): AppState {
        var s = state
        if (s.activeTimer?.sessionId == sessionId) s = stopTimer(s, System.currentTimeMillis(), markDone = false)
        return s.copy(sessions = s.sessions.map { if (it.id == sessionId) it.copy(status = SessionStatus.SKIPPED) else it })
    }

    /** "I finished this step" - also used when a step is done earlier than planned. */
    fun completeSubtask(state: AppState, goalId: String, subtaskId: String): AppState {
        val activeId = state.activeTimer?.sessionId
        val goals = state.goals.map { g ->
            if (g.id != goalId) g else {
                val subs = g.subtasks.map { if (it.id == subtaskId) it.copy(completed = true) else it }
                g.copy(subtasks = subs, status = if (subs.all { it.completed }) GoalStatus.COMPLETED else g.status)
            }
        }
        val sessions = state.sessions.filterNot {
            it.subtaskId == subtaskId && it.status == SessionStatus.PLANNED && it.id != activeId
        }
        return state.copy(goals = goals, sessions = sessions)
    }

    fun reopenSubtask(state: AppState, goalId: String, subtaskId: String): AppState = state.copy(
        goals = state.goals.map { g ->
            if (g.id != goalId) g else g.copy(
                subtasks = g.subtasks.map { if (it.id == subtaskId) it.copy(completed = false) else it },
                status = GoalStatus.ACTIVE,
            )
        },
    )

    // ---------- queries ----------

    fun sessionsOn(state: AppState, date: LocalDate): List<PlannedSession> =
        state.sessions.filter { it.date == date.toString() }.sortedBy { it.startMinute }

    /** Seconds logged per day (ISO date -> seconds), using the device's zone. */
    fun loggedPerDay(state: AppState, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Map<String, Long> =
        state.logs.groupBy { java.time.Instant.ofEpochMilli(it.startEpochMs).atZone(zone).toLocalDate().toString() }
            .mapValues { (_, l) -> l.sumOf { it.seconds } }

    fun goalProgress(state: AppState, goal: Goal): Float {
        if (goal.subtasks.isEmpty()) return 0f
        val total = goal.subtasks.sumOf { it.estimateMinutes }.coerceAtLeast(1)
        val done = goal.subtasks.filter { it.completed }.sumOf { it.estimateMinutes }
        return done.toFloat() / total
    }

    /** Subtask from a draft, with a fresh id. */
    fun subtaskFrom(d: SubtaskDraft) = Subtask(newId(), d.title.trim(), d.estimateMinutes, d.difficulty, d.notes)
}
