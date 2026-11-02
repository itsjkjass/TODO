package com.pacemate.core

import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchedulerTest {
    private var n = 0
    private val scheduler = Scheduler { "s${n++}" }
    private val monday = LocalDate.of(2026, 11, 2) // a Monday
    private val profile = UserProfile()

    private fun goal(id: String, deadline: LocalDate, vararg minutes: Int, difficulty: Int = 2) = Goal(
        id = id, title = "Goal $id", deadline = deadline.toString(),
        subtasks = minutes.mapIndexed { i, m -> Subtask("$id-$i", "Step $i", m, difficulty) },
    )

    @Test
    fun freeWindowsRespectWorkMealsAndBuffers() {
        val w = scheduler.freeWindows(profile, monday)
        // wake 7:00 + 30 buffer; work 9-17; dinner 19:30-20:15; sleep 23:00 - 60 wind-down
        assertEquals(8 * 60, w.first().start) // wake 7:00 + 30 buffer, then breakfast 7:30-8:00
        assertTrue(w.none { it.start < 17 * 60 && it.end > 9 * 60 }, "no time inside work hours")
        assertTrue(w.none { it.start < 20 * 60 + 15 && it.end > 19 * 60 + 30 }, "dinner is free")
        assertEquals(22 * 60, w.last().end)
    }

    @Test
    fun neverExceedsComfortableDailyLimit() {
        val g = goal("a", monday.plusDays(30), 600, 600, 600)
        val result = scheduler.plan(profile, listOf(g), PaceStats(), monday, 0)
        val perDay = result.sessions.groupBy { it.date }.mapValues { (_, s) -> s.sumOf { it.durationMinutes } }
        for ((date, minutes) in perDay) {
            val d = LocalDate.parse(date)
            val limit = if (d.dayOfWeek.value in profile.routine.workDays) 120 else 240
            assertTrue(minutes <= limit, "$date has $minutes min > $limit")
        }
    }

    @Test
    fun spreadsWorkEvenlyInsteadOfCramming() {
        // 4h of work, 10 days -> should not all land on the first day or two.
        val g = goal("a", monday.plusDays(10), 60, 60, 60, 60)
        val result = scheduler.plan(profile, listOf(g), PaceStats(), monday, 0)
        val days = result.sessions.map { it.date }.toSet()
        assertTrue(days.size >= 4, "expected work spread over several days, got $days")
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun finishesBeforeDeadlineWithBuffer() {
        val deadline = monday.plusDays(7)
        val g = goal("a", deadline, 90, 90, 60)
        val result = scheduler.plan(profile, listOf(g), PaceStats(), monday, 0)
        val last = result.sessions.maxOf { LocalDate.parse(it.date) }
        assertTrue(last.isBefore(deadline), "last session $last should be before deadline $deadline")
    }

    @Test
    fun sessionsDoNotOverlapAndFitFreeTime() {
        val goals = listOf(
            goal("a", monday.plusDays(5), 120, 120),
            goal("b", monday.plusDays(3), 90, 45, difficulty = 3),
        )
        val result = scheduler.plan(profile, goals, PaceStats(), monday, 0)
        for ((date, list) in result.sessions.groupBy { it.date }) {
            val sorted = list.sortedBy { it.startMinute }
            sorted.zipWithNext().forEach { (x, y) -> assertTrue(x.endMinute <= y.startMinute, "overlap on $date") }
            val free = scheduler.freeWindows(profile, LocalDate.parse(date))
            sorted.forEach { s -> assertTrue(free.any { s.startMinute >= it.start && s.endMinute <= it.end }, "$s outside free time") }
        }
    }

    @Test
    fun keepsStepOrderWithinAGoal() {
        val g = goal("a", monday.plusDays(2), 40, 40, 40)
        val result = scheduler.plan(profile, listOf(g), PaceStats(), monday, 0)
        val order = result.sessions.sortedWith(compareBy({ it.date }, { it.startMinute })).map { it.subtaskId }
        assertEquals(order.sorted(), order)
    }

    @Test
    fun warnsWhenDeadlineIsUnrealistic() {
        val g = goal("a", monday.plusDays(1), 600, 600)
        val result = scheduler.plan(profile, listOf(g), PaceStats(), monday, 0)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun startsAfterCurrentTimeToday() {
        val g = goal("a", monday.plusDays(5), 60)
        val result = scheduler.plan(profile, listOf(g), PaceStats(), monday, 18 * 60)
        result.sessions.filter { it.date == monday.toString() }.forEach { assertTrue(it.startMinute >= 18 * 60) }
    }

    @Test
    fun slowerPaceMeansMorePlannedTime() {
        val g = goal("a", monday.plusDays(20), 120)
        val avg = scheduler.plan(profile, listOf(g), PaceStats(1.0), monday, 0).sessions.sumOf { it.durationMinutes }
        val slow = scheduler.plan(profile, listOf(g), PaceStats(1.5), monday, 0).sessions.sumOf { it.durationMinutes }
        assertTrue(slow > avg, "slow=$slow avg=$avg")
    }

    @Test
    fun paceLearnsFromLogger() {
        var stats = PaceStats(1.0)
        repeat(6) { stats = PaceModel.learn(stats, "study", plannedMinutes = 60, actualSeconds = 90 * 60) }
        assertTrue(stats.factor > 1.3, "factor ${stats.factor}")
        assertTrue(PaceModel.factorFor(stats, "study") > 1.3)
    }

    @Test
    fun splitRespectsFocusBlock() {
        val parts = PaceModel.splitIntoSessions(200, 50)
        assertTrue(parts.all { it in 15..50 }, "$parts")
        assertTrue(parts.sum() >= 200)
    }

    @Test
    fun offlineBreakdownScalesWithEstimate() = runBlocking {
        val r = OfflineBreakdowner.breakdown(GoalRequest("Write thesis chapter", "", "2026-12-01", 10.0), profile, PaceStats(), "2026-11-02")
        assertEquals(600, r.subtasks.sumOf { it.estimateMinutes }, "total")
        assertEquals("writing", r.category)
    }

    @Test
    fun routineDraftMerges() {
        val merged = profile.merge(RoutineDraft(wakeTime = "06:15", workDays = listOf(1, 2, 3), peakEnergy = "evening"))
        assertEquals(6 * 60 + 15, merged.routine.wakeTime)
        assertEquals(setOf(1, 2, 3), merged.routine.workDays)
        assertEquals(EnergyPeak.EVENING, merged.preferences.peakEnergy)
        assertEquals(profile.routine.sleepTime, merged.routine.sleepTime)
    }
}
