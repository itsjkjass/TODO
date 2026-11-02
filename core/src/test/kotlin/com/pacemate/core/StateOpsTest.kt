package com.pacemate.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StateOpsTest {
    private val monday = LocalDate.of(2026, 11, 2)

    private fun planned(): AppState {
        val goal = Goal(
            id = "g", title = "Learn Kotlin", deadline = monday.plusDays(14).toString(), category = "study",
            subtasks = listOf(Subtask("a", "Read basics", 60), Subtask("b", "Build a small app", 120)),
        )
        val s = StateOps.upsertGoal(AppState(onboarded = true), goal)
        return StateOps.replan(s, monday, 8 * 60)
    }

    @Test
    fun replanCreatesSessions() {
        val s = planned()
        assertTrue(s.sessions.isNotEmpty())
        assertTrue(s.sessions.all { it.status == SessionStatus.PLANNED })
    }

    @Test
    fun timerLogsTimeAndCompletes() {
        var s = planned()
        val first = s.sessions.first { it.subtaskId == "a" }
        val t0 = 1_000_000L
        s = StateOps.startTimer(s, first.id, t0)
        val timer = assertNotNull(s.activeTimer)
        assertEquals(t0 + first.durationMinutes * 60_000L, timer.plannedEndEpochMs)

        s = StateOps.extendTimer(s, 10, t0)
        assertEquals(t0 + (first.durationMinutes + 10) * 60_000L, s.activeTimer!!.plannedEndEpochMs)

        val t1 = t0 + 30 * 60_000L
        s = StateOps.stopTimer(s, t1, markDone = true)
        assertNull(s.activeTimer)
        assertEquals(1, s.logs.size)
        assertEquals(30 * 60L, s.logs.single().seconds)
        assertEquals(SessionStatus.DONE, s.sessions.first { it.id == first.id }.status)
        assertEquals(1, s.pace.samples)
    }

    @Test
    fun startingAnotherTimerStopsAndLogsTheFirst() {
        var s = planned()
        val (x, y) = s.sessions.take(2)
        s = StateOps.startTimer(s, x.id, 0)
        s = StateOps.startTimer(s, y.id, 10 * 60_000L)
        assertEquals(y.id, s.activeTimer!!.sessionId)
        assertEquals(1, s.logs.size)
        assertEquals(600L, s.sessions.first { it.id == x.id }.loggedSeconds)
    }

    @Test
    fun completingAllSessionsCompletesSubtaskAndGoal() {
        var s = planned()
        for (id in s.sessions.map { it.id }) s = StateOps.markSessionDone(s, id)
        assertTrue(s.goals.single().subtasks.all { it.completed })
        assertEquals(GoalStatus.COMPLETED, s.goals.single().status)
    }

    @Test
    fun finishingStepEarlyRemovesItsRemainingSessions() {
        var s = planned()
        s = StateOps.completeSubtask(s, "g", "b")
        assertTrue(s.sessions.none { it.subtaskId == "b" })
        assertTrue(s.goals.single().subtasks.first { it.id == "b" }.completed)
    }

    @Test
    fun replanKeepsDoneSessionsAndReducesRemainingWork() {
        var s = planned()
        val before = s.sessions.filter { it.subtaskId == "b" }.sumOf { it.durationMinutes }
        val firstB = s.sessions.first { it.subtaskId == "b" }
        s = StateOps.markSessionDone(s, firstB.id)
        s = StateOps.replan(s, monday, 8 * 60)
        assertTrue(s.sessions.any { it.id == firstB.id && it.status == SessionStatus.DONE })
        val stillPlanned = s.sessions.filter { it.subtaskId == "b" && it.status == SessionStatus.PLANNED }.sumOf { it.durationMinutes }
        assertTrue(stillPlanned < before)
    }

    @Test
    fun stateRoundTripsThroughJson() {
        val s = planned()
        val json = PaceJson.encodeToString(AppState.serializer(), s)
        assertEquals(s, PaceJson.decodeFromString(AppState.serializer(), json))
    }
}
