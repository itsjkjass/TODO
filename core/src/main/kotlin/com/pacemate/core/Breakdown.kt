package com.pacemate.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** Result of breaking a goal into steps (AI or offline). */
@Serializable
data class BreakdownResult(
    val subtasks: List<SubtaskDraft>,
    val category: String = "general",
    val note: String = "",
)

@Serializable
data class SubtaskDraft(
    val title: String,
    val estimateMinutes: Int,
    val difficulty: Int = 2,
    val notes: String = "",
)

/** Request for a goal breakdown. */
data class GoalRequest(
    val title: String,
    val details: String,
    val deadline: String,
    /** The user's own guess of total effort in hours, if they gave one. */
    val userEstimateHours: Double? = null,
)

interface GoalBreakdowner {
    suspend fun breakdown(request: GoalRequest, profile: UserProfile, pace: PaceStats, today: String): BreakdownResult
}

/**
 * Works without network / API key: a sensible generic structure scaled to the
 * user's effort estimate (or 5 hours by default).
 */
object OfflineBreakdowner : GoalBreakdowner {
    override suspend fun breakdown(request: GoalRequest, profile: UserProfile, pace: PaceStats, today: String): BreakdownResult {
        val totalMinutes = ((request.userEstimateHours ?: 5.0) * 60).roundToInt().coerceAtLeast(30)
        val title = request.title.trim()
        val steps = mutableListOf<SubtaskDraft>()
        if (totalMinutes <= 60) {
            steps += SubtaskDraft("Do: $title", totalMinutes, 2)
            return BreakdownResult(steps, guessCategory(request), "Small task, kept as a single step.")
        }
        val plan = (totalMinutes * 0.10).roundToInt().coerceIn(15, 60)
        val review = (totalMinutes * 0.15).roundToInt().coerceIn(15, 120)
        val wrap = 20
        val core = totalMinutes - plan - review - wrap
        steps += SubtaskDraft("Plan \"$title\": define what done looks like, list materials", plan, 1)
        // Core work split into at most 6 parts of roughly 60-120 minutes.
        val parts = (core / 90.0).roundToInt().coerceIn(1, 6)
        val each = core / parts
        for (i in 1..parts) {
            steps += SubtaskDraft(
                if (parts == 1) "Main work on \"$title\"" else "Main work on \"$title\" - part $i of $parts",
                each, if (i == 1) 3 else 2,
            )
        }
        steps += SubtaskDraft("Review and improve \"$title\"", review, 2)
        steps += SubtaskDraft("Final check and wrap up", wrap, 1)
        return BreakdownResult(
            steps, guessCategory(request),
            "Offline breakdown (add an Anthropic API key in Settings for a tailored, AI-made plan).",
        )
    }

    private fun guessCategory(r: GoalRequest): String {
        val t = (r.title + " " + r.details).lowercase()
        return when {
            listOf("study", "exam", "learn", "course", "read", "test").any { it in t } -> "study"
            listOf("write", "essay", "report", "blog", "thesis", "article").any { it in t } -> "writing"
            listOf("code", "app", "website", "program", "build", "develop").any { it in t } -> "coding"
            listOf("clean", "move", "house", "garden", "repair", "organize").any { it in t } -> "home"
            listOf("run", "gym", "workout", "fitness", "train").any { it in t } -> "fitness"
            else -> "general"
        }
    }
}

/** Routine + preferences extracted from free-text answers in onboarding. */
@Serializable
data class RoutineDraft(
    val wakeTime: String? = null,
    val sleepTime: String? = null,
    val workStart: String? = null,
    val workEnd: String? = null,
    val workDays: List<Int>? = null,
    val commuteMinutes: Int? = null,
    val meals: List<BlockDraft>? = null,
    val fixedBlocks: List<BlockDraft>? = null,
    val focusBlockMinutes: Int? = null,
    val breakMinutes: Int? = null,
    val peakEnergy: String? = null,
    val maxTaskHoursWorkday: Double? = null,
    val maxTaskHoursFreeDay: Double? = null,
    val restDays: List<Int>? = null,
    val summary: String = "",
)

@Serializable
data class BlockDraft(
    val label: String,
    val start: String,
    val durationMinutes: Int,
    val days: List<Int> = emptyList(),
)

object Times {
    /** "07:30" / "7:30" -> 450; null if unparsable. */
    fun parse(hhmm: String?): Int? {
        if (hhmm == null) return null
        val parts = hhmm.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..24 || m !in 0..59) return null
        return (h * 60 + m).coerceAtMost(24 * 60)
    }

    fun format(minute: Int): String {
        val m = ((minute % (24 * 60)) + 24 * 60) % (24 * 60)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }
}

/** Applies an AI-extracted [RoutineDraft] on top of an existing profile. */
fun UserProfile.merge(d: RoutineDraft): UserProfile {
    fun blocks(list: List<BlockDraft>?) = list?.mapNotNull { b ->
        Times.parse(b.start)?.let { TimeBlock(b.label, it, b.durationMinutes.coerceIn(5, 600), b.days.filter { x -> x in 1..7 }.toSet()) }
    }
    val r = routine
    val p = preferences
    return copy(
        routine = r.copy(
            wakeTime = Times.parse(d.wakeTime) ?: r.wakeTime,
            sleepTime = Times.parse(d.sleepTime) ?: r.sleepTime,
            workStart = Times.parse(d.workStart) ?: r.workStart,
            workEnd = Times.parse(d.workEnd) ?: r.workEnd,
            workDays = d.workDays?.filter { it in 1..7 }?.toSet() ?: r.workDays,
            commuteMinutes = d.commuteMinutes?.coerceIn(0, 240) ?: r.commuteMinutes,
            meals = blocks(d.meals) ?: r.meals,
            fixedBlocks = blocks(d.fixedBlocks) ?: r.fixedBlocks,
        ),
        preferences = p.copy(
            focusBlockMinutes = d.focusBlockMinutes?.coerceIn(15, 180) ?: p.focusBlockMinutes,
            breakMinutes = d.breakMinutes?.coerceIn(0, 60) ?: p.breakMinutes,
            peakEnergy = d.peakEnergy?.let { runCatching { EnergyPeak.valueOf(it.uppercase()) }.getOrNull() } ?: p.peakEnergy,
            maxTaskHoursWorkday = d.maxTaskHoursWorkday?.coerceIn(0.0, 16.0) ?: p.maxTaskHoursWorkday,
            maxTaskHoursFreeDay = d.maxTaskHoursFreeDay?.coerceIn(0.0, 16.0) ?: p.maxTaskHoursFreeDay,
            restDays = d.restDays?.filter { it in 1..7 }?.toSet() ?: p.restDays,
        ),
    )
}

val PaceJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    explicitNulls = false
}
