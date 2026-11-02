package com.pacemate.core

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@Serializable
data class PlanWarning(
    val goalId: String,
    val goalTitle: String,
    val message: String,
    /** Date the goal would realistically be finished at a comfortable pace (ISO), if known. */
    val projectedFinish: String? = null,
)

data class PlanResult(
    val sessions: List<PlannedSession>,
    val warnings: List<PlanWarning>,
)

data class FreeWindow(val start: MinuteOfDay, val end: MinuteOfDay) {
    val length get() = end - start
}

/**
 * Turns goals + subtasks into a calendar of time-boxed sessions.
 *
 * Design principles ("no pressure"):
 *  - Never plan more than the user's own comfortable daily limit.
 *  - Spread each goal evenly over the days left until (deadline - buffer)
 *    instead of cramming, so every day has a light, predictable load.
 *  - Respect sleep, wake-up buffer, wind-down, work + commute, meals, fixed
 *    commitments and rest days.
 *  - Breaks between sessions; demanding work goes into the user's peak-energy window.
 *  - Durations come from [PaceModel], i.e. how fast *this* human really works.
 */
class Scheduler(
    private val idGen: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    companion object {
        /** How far past the last deadline we look when a goal doesn't fit. */
        const val OVERFLOW_DAYS = 60L
        const val MAX_HORIZON_DAYS = 400L
        /** Leave at least this much of the free window unplanned (life happens). */
        const val MAX_FREE_TIME_USE = 0.7
    }

    private data class Chunk(
        val goal: Goal,
        val subtask: Subtask,
        val minutes: Int,
        val partIndex: Int,
        val partCount: Int,
    )

    /**
     * @param today first day to plan
     * @param nowMinute current minute of [today]; nothing is planned before it
     * @param keep sessions that must stay as they are (done / skipped / in progress)
     */
    fun plan(
        profile: UserProfile,
        goals: List<Goal>,
        pace: PaceStats,
        today: LocalDate,
        nowMinute: MinuteOfDay,
        keep: List<PlannedSession> = emptyList(),
    ): PlanResult {
        val prefs = profile.preferences
        val active = goals.filter { it.status == GoalStatus.ACTIVE }

        // 1. Remaining work per goal, as an ordered queue of chunks.
        val queues = LinkedHashMap<String, ArrayDeque<Chunk>>()
        for (goal in active) {
            val q = ArrayDeque<Chunk>()
            for (st in goal.subtasks.filter { !it.completed }) {
                val total = PaceModel.realisticMinutes(st.estimateMinutes, st.difficulty, pace, goal.category)
                val doneMinutes = keep.filter { it.subtaskId == st.id && it.status == SessionStatus.DONE }
                    .sumOf { it.durationMinutes }
                val remaining = total - doneMinutes
                if (remaining < 10) continue
                val parts = PaceModel.splitIntoSessions(remaining, prefs.focusBlockMinutes)
                parts.forEachIndexed { i, m -> q.addLast(Chunk(goal, st, m, i, parts.size)) }
            }
            if (q.isNotEmpty()) queues[goal.id] = q
        }

        val goalsById = active.associateBy { it.id }
        val targetDate = active.associate { g ->
            val deadline = LocalDate.parse(g.deadline)
            val target = deadline.minusDays(prefs.deadlineBufferDays.toLong())
            g.id to if (target.isBefore(today)) deadline else target
        }

        val lastDeadline = active.maxOfOrNull { LocalDate.parse(it.deadline) } ?: today
        val horizonEnd = minOf(
            maxOf(lastDeadline, today).plusDays(OVERFLOW_DAYS),
            today.plusDays(MAX_HORIZON_DAYS),
        )

        val newSessions = mutableListOf<PlannedSession>()
        val finishDate = mutableMapOf<String, LocalDate>()

        var day = today
        while (!day.isAfter(horizonEnd) && queues.values.any { it.isNotEmpty() }) {
            val dow = day.dayOfWeek.value
            val earliest = if (day == today) nowMinute + 5 else 0
            var windows = freeWindows(profile, day, earliest)
            // Existing kept sessions occupy time too.
            keep.filter { it.date == day.toString() && it.status != SessionStatus.SKIPPED }.forEach { s ->
                windows = subtract(windows, s.startMinute, s.endMinute + prefs.breakMinutes)
            }
            val keptMinutes = keep.filter { it.date == day.toString() && it.status != SessionStatus.SKIPPED }
                .sumOf { it.durationMinutes }

            val isRestDay = dow in prefs.restDays
            val isWorkDay = dow in profile.routine.workDays
            val freeTotal = windows.sumOf { it.length }
            val comfortable = ((if (isWorkDay) prefs.maxTaskHoursWorkday else prefs.maxTaskHoursFreeDay) * 60).toInt()
            var capacity = min(comfortable, (freeTotal * MAX_FREE_TIME_USE).toInt()) - keptMinutes
            if (day == today) {
                // Only the part of today that is left.
                capacity = min(capacity, (freeTotal * MAX_FREE_TIME_USE).toInt())
            }

            // On a rest day only work that is already late / due is allowed.
            val picked = mutableListOf<Chunk>()
            if (capacity > 0) {
                val order = queues.keys
                    .filter { queues[it]!!.isNotEmpty() }
                    .sortedWith(compareBy({ targetDate[it] }, { goalsById[it]!!.priority }))

                // Pass 1: each goal gets its fair, evenly spread share for today.
                val allocated = mutableMapOf<String, Int>()
                for (gid in order) {
                    val q = queues[gid]!!
                    if (isRestDay && !day.isAfter(targetDate[gid]!!.minusDays(1))) continue
                    val remaining = q.sumOf { it.minutes }
                    val daysLeft = plannableDays(profile, day, targetDate[gid]!!)
                    val quota = ceil(remaining.toDouble() / daysLeft).toInt()
                    var used = 0
                    while (q.isNotEmpty() && used < quota && capacity - q.first().minutes >= 0) {
                        val c = q.removeFirst()
                        picked += c
                        used += c.minutes
                        capacity -= c.minutes
                    }
                    allocated[gid] = used
                }
                // Pass 2: goals that are already past their target date get any spare comfortable time.
                for (gid in order) {
                    if (day.isBefore(targetDate[gid]!!)) continue
                    val q = queues[gid]!!
                    while (q.isNotEmpty() && capacity - q.first().minutes >= 0) {
                        val c = q.removeFirst()
                        picked += c
                        capacity -= c.minutes
                    }
                }
            }

            // 2. Place picked chunks into today's free windows.
            val placed = place(picked, windows, prefs)
            val unplaced = picked - placed.map { it.first }.toSet()
            // Put back anything that didn't physically fit (fragmented day), keeping order.
            unplaced.reversed().forEach { queues[it.goal.id]!!.addFirst(it) }

            for ((chunk, start) in placed) {
                val title = if (chunk.partCount > 1) "${chunk.subtask.title} (${chunk.partIndex + 1}/${chunk.partCount})"
                else chunk.subtask.title
                newSessions += PlannedSession(
                    id = idGen(),
                    goalId = chunk.goal.id,
                    subtaskId = chunk.subtask.id,
                    title = title,
                    goalTitle = chunk.goal.title,
                    date = day.toString(),
                    startMinute = start,
                    durationMinutes = chunk.minutes,
                    difficulty = chunk.subtask.difficulty,
                )
                finishDate[chunk.goal.id] = day
            }
            day = day.plusDays(1)
        }

        // 3. Warnings: deadlines that can't be met at a comfortable pace.
        val warnings = mutableListOf<PlanWarning>()
        for (g in active) {
            val deadline = LocalDate.parse(g.deadline)
            val leftover = queues[g.id]?.sumOf { it.minutes } ?: 0
            val finish = finishDate[g.id]
            when {
                leftover > 0 -> warnings += PlanWarning(
                    g.id, g.title,
                    "About ${hours(leftover)} of work for \"${g.title}\" doesn't fit in your comfortable schedule. " +
                        "Consider extending the deadline, trimming the scope, or raising your daily task limit.",
                    null,
                )
                finish != null && finish.isAfter(deadline) -> warnings += PlanWarning(
                    g.id, g.title,
                    "At a comfortable pace \"${g.title}\" finishes on $finish, " +
                        "${ChronoUnit.DAYS.between(deadline, finish)} day(s) after the deadline ($deadline). " +
                        "Move the deadline, drop a subtask, or allow a bit more time per day.",
                    finish.toString(),
                )
            }
            if (deadline.isBefore(today) && g.subtasks.any { !it.completed }) {
                warnings += PlanWarning(g.id, g.title, "The deadline for \"${g.title}\" has passed.", finish?.toString())
            }
        }

        return PlanResult(
            sessions = (keep + newSessions).sortedWith(compareBy({ it.date }, { it.startMinute })),
            warnings = warnings,
        )
    }

    /** Days on which work can be planned, from [from] up to and including [to]. At least 1. */
    private fun plannableDays(profile: UserProfile, from: LocalDate, to: LocalDate): Int {
        if (to.isBefore(from)) return 1
        var n = 0
        var d = from
        while (!d.isAfter(to)) {
            if (d.dayOfWeek.value !in profile.preferences.restDays) n++
            d = d.plusDays(1)
        }
        return max(1, n)
    }

    /**
     * Places chunks into windows, inserting a break after each session.
     * Demanding chunks (difficulty 3) try the peak-energy period first.
     * Returns (chunk, startMinute) in chronological order; chunks of the
     * same goal keep their original order.
     */
    private fun place(
        chunks: List<Chunk>,
        windows: List<FreeWindow>,
        prefs: Preferences,
    ): List<Pair<Chunk, Int>> {
        if (chunks.isEmpty()) return emptyList()
        val (peakStart, peakEnd) = peakRange(prefs.peakEnergy)
        var free = windows
        val slots = mutableListOf<Pair<Chunk, Int>>()

        // Hard work first so it gets the best slots; then the rest.
        val ordered = chunks.sortedByDescending { it.subtask.difficulty }
        for (c in ordered) {
            val need = c.minutes
            fun fits(w: FreeWindow, from: Int) = from + need <= w.end
            var start: Int? = null
            if (c.subtask.difficulty >= 3) {
                start = free.firstNotNullOfOrNull { w ->
                    val s = max(w.start, peakStart)
                    if (s < peakEnd && fits(w, s)) s else null
                }
            }
            if (start == null) start = free.firstOrNull { fits(it, it.start) }?.start
            if (start == null) continue
            slots += c to start
            free = subtract(free, start, start + need + prefs.breakMinutes)
        }

        // Re-assign so that within a goal, earlier parts get earlier slots.
        val byGoal = slots.groupBy { it.first.goal.id }
        val result = mutableListOf<Pair<Chunk, Int>>()
        for ((_, list) in byGoal) {
            val chunksInOrder = list.map { it.first }.sortedBy { chunks.indexOf(it) }
            val startsInOrder = list.map { it.second }.sorted()
            // Durations may differ; only swap if each chunk still fits the earlier slot's window.
            val swapped = chunksInOrder.zip(startsInOrder)
            val ok = swapped.all { (c, s) ->
                val orig = list.first { it.second == s }.first
                c.minutes <= orig.minutes
            }
            result += if (ok) swapped else list
        }
        return result.sortedBy { it.second }
    }

    private fun peakRange(peak: EnergyPeak): Pair<Int, Int> = when (peak) {
        EnergyPeak.MORNING -> 6 * 60 to 12 * 60
        EnergyPeak.AFTERNOON -> 12 * 60 to 17 * 60
        EnergyPeak.EVENING -> 17 * 60 to 23 * 60
    }

    /** Free windows of a day after removing sleep, buffers, work, commute, meals and fixed blocks. */
    fun freeWindows(profile: UserProfile, day: LocalDate, earliest: Int = 0): List<FreeWindow> {
        val r = profile.routine
        val p = profile.preferences
        val dow = day.dayOfWeek.value
        val dayStart = max(r.wakeTime + p.morningBufferMinutes, earliest)
        val sleep = if (r.sleepTime <= r.wakeTime) r.sleepTime + 24 * 60 else r.sleepTime
        val dayEnd = min(sleep - p.windDownMinutes, 24 * 60)
        if (dayEnd - dayStart < 15) return emptyList()
        var windows = listOf(FreeWindow(dayStart, dayEnd))
        if (dow in r.workDays && r.workEnd > r.workStart) {
            windows = subtract(windows, r.workStart - r.commuteMinutes, r.workEnd + r.commuteMinutes)
        }
        for (b in r.meals + r.fixedBlocks) {
            if (b.appliesOn(dow)) windows = subtract(windows, b.start, b.end)
        }
        return windows.filter { it.length >= 15 }
    }

    private fun subtract(windows: List<FreeWindow>, start: Int, end: Int): List<FreeWindow> =
        windows.flatMap { w ->
            if (end <= w.start || start >= w.end) listOf(w)
            else listOfNotNull(
                if (start > w.start) FreeWindow(w.start, start) else null,
                if (end < w.end) FreeWindow(end, w.end) else null,
            )
        }

    private fun hours(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h == 0) "$m min" else if (m == 0) "${h}h" else "${h}h ${m}m"
    }
}
