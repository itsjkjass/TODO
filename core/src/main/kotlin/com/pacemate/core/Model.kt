package com.pacemate.core

import kotlinx.serialization.Serializable

/** Minutes since midnight, e.g. 7:30 -> 450. */
typealias MinuteOfDay = Int

@Serializable
enum class EnergyPeak { MORNING, AFTERNOON, EVENING }

/**
 * How fast the user says they work compared with an "average person" estimate.
 * This is only the starting point: [PaceStats] learns the real pace from the logger.
 */
@Serializable
enum class SelfRatedPace(val factor: Double, val label: String) {
    RELAXED(1.35, "I like to take my time"),
    AVERAGE(1.0, "About average"),
    QUICK(0.85, "I'm usually quick"),
}

@Serializable
data class TimeBlock(
    val label: String,
    val start: MinuteOfDay,
    val durationMinutes: Int,
    /** ISO day-of-week numbers (1 = Monday .. 7 = Sunday). Empty = every day. */
    val days: Set<Int> = emptySet(),
) {
    val end: MinuteOfDay get() = start + durationMinutes
    fun appliesOn(dayOfWeek: Int) = days.isEmpty() || dayOfWeek in days
}

/** The user's daily routine, gathered during onboarding. */
@Serializable
data class Routine(
    val wakeTime: MinuteOfDay = 7 * 60,
    val sleepTime: MinuteOfDay = 23 * 60,
    val workStart: MinuteOfDay = 9 * 60,
    val workEnd: MinuteOfDay = 17 * 60,
    val workDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** One-way commute; blocked before and after work on work days. */
    val commuteMinutes: Int = 0,
    val meals: List<TimeBlock> = listOf(
        TimeBlock("Breakfast", 7 * 60 + 30, 30),
        TimeBlock("Lunch", 13 * 60, 45),
        TimeBlock("Dinner", 19 * 60 + 30, 45),
    ),
    /** Other fixed commitments: gym, school run, classes, prayer, etc. */
    val fixedBlocks: List<TimeBlock> = emptyList(),
)

@Serializable
data class Preferences(
    /** Longest single focus session the user is comfortable with. */
    val focusBlockMinutes: Int = 50,
    val breakMinutes: Int = 10,
    val peakEnergy: EnergyPeak = EnergyPeak.MORNING,
    /** Personal-task hours per day the user is happy to commit to (outside work). */
    val maxTaskHoursWorkday: Double = 2.0,
    val maxTaskHoursFreeDay: Double = 4.0,
    val selfRatedPace: SelfRatedPace = SelfRatedPace.AVERAGE,
    /** Free time kept after waking before any task is planned. */
    val morningBufferMinutes: Int = 30,
    /** No tasks planned in the last N minutes before sleep. */
    val windDownMinutes: Int = 60,
    /** Days with no planned tasks at all (ISO day-of-week). */
    val restDays: Set<Int> = emptySet(),
    /** Try to finish this many days before each deadline (safety margin). */
    val deadlineBufferDays: Int = 1,
    /** Free-form notes for the AI ("I hate mornings", "I study better with music"...). */
    val notes: String = "",
)

@Serializable
data class UserProfile(
    val name: String = "",
    val routine: Routine = Routine(),
    val preferences: Preferences = Preferences(),
)

@Serializable
enum class GoalStatus { ACTIVE, COMPLETED, ARCHIVED }

@Serializable
data class Subtask(
    val id: String,
    val title: String,
    /** Estimate for an average person, in minutes (before personal pace is applied). */
    val estimateMinutes: Int,
    /** 1 = light, 2 = normal, 3 = demanding (needs peak energy). */
    val difficulty: Int = 2,
    val notes: String = "",
    val completed: Boolean = false,
)

@Serializable
data class Goal(
    val id: String,
    val title: String,
    val details: String = "",
    /** ISO date (yyyy-MM-dd). */
    val deadline: String,
    /** 1 = high, 2 = normal, 3 = low. */
    val priority: Int = 2,
    val category: String = "general",
    val subtasks: List<Subtask> = emptyList(),
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAtEpochMs: Long = 0,
    /** Short note from the AI on how the goal was broken down. */
    val planNote: String = "",
)

@Serializable
enum class SessionStatus { PLANNED, DONE, SKIPPED }

/** One time-boxed block on the calendar: a chunk of one subtask. */
@Serializable
data class PlannedSession(
    val id: String,
    val goalId: String,
    val subtaskId: String,
    val title: String,
    val goalTitle: String,
    /** ISO date. */
    val date: String,
    val startMinute: MinuteOfDay,
    val durationMinutes: Int,
    val difficulty: Int = 2,
    val status: SessionStatus = SessionStatus.PLANNED,
    /** Seconds recorded by the logger for this session. */
    val loggedSeconds: Long = 0,
) {
    val endMinute: MinuteOfDay get() = startMinute + durationMinutes
}

/** One logger entry: a stretch of time actually spent on a session. */
@Serializable
data class TimeLog(
    val id: String,
    val sessionId: String,
    val goalId: String,
    val subtaskId: String,
    val title: String,
    val category: String = "general",
    val startEpochMs: Long,
    val endEpochMs: Long,
    val plannedMinutes: Int,
) {
    val seconds: Long get() = (endEpochMs - startEpochMs).coerceAtLeast(0) / 1000
}

/** The task timer that is currently running, if any. */
@Serializable
data class ActiveTimer(
    val sessionId: String,
    val startedAtEpochMs: Long,
    /** When the planned time for the session runs out (notification fires then). */
    val plannedEndEpochMs: Long,
    val extendedTimes: Int = 0,
)

@Serializable
data class AppState(
    val onboarded: Boolean = false,
    val profile: UserProfile = UserProfile(),
    val goals: List<Goal> = emptyList(),
    val sessions: List<PlannedSession> = emptyList(),
    val logs: List<TimeLog> = emptyList(),
    val activeTimer: ActiveTimer? = null,
    val pace: PaceStats = PaceStats(),
    val planWarnings: List<PlanWarning> = emptyList(),
    val lastPlannedEpochMs: Long = 0,
)
