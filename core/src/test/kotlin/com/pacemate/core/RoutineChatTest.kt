package com.pacemate.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineChatTest {

    @Test
    fun looseTimes() {
        assertEquals(7 * 60, Loose.time("7"))
        assertEquals(7 * 60, Loose.time("7am"))
        assertEquals(6 * 60 + 30, Loose.time("around 6:30"))
        assertEquals(6 * 60 + 30, Loose.time("half past six"))
        assertEquals(19 * 60, Loose.time("7 pm"))
        assertEquals(19 * 60 + 15, Loose.time("7.15 p.m."))
        assertEquals(23 * 60, Loose.time("11", preferEvening = true))
        assertEquals(22 * 60 + 30, Loose.time("10:30", preferEvening = true))
        assertEquals(1 * 60, Loose.time("1", preferEvening = true))
        assertEquals(0, Loose.time("midnight", preferEvening = true))
        assertEquals(0, Loose.time("12", preferEvening = true))
        assertEquals(12 * 60, Loose.time("noon"))
        assertNull(Loose.time("whenever"))
    }

    @Test
    fun looseRangesDaysAndDurations() {
        assertEquals(9 * 60 to 17 * 60, Loose.range("9 to 5"))
        assertEquals(8 * 60 + 30 to 16 * 60 + 30, Loose.range("8:30 - 16:30"))
        assertEquals(9 * 60 to 18 * 60, Loose.range("9am until 6pm"))
        assertEquals((1..5).toSet(), Loose.days("Mon–Fri"))
        assertEquals((1..5).toSet(), Loose.days("weekdays"))
        assertEquals((1..6).toSet(), Loose.days("monday to saturday"))
        assertEquals(setOf(1, 3, 5), Loose.days("Mon, Wed and Fri"))
        assertEquals((1..7).toSet(), Loose.days("every day"))
        assertEquals(setOf(6, 7), Loose.days("weekends"))
        assertEquals(0, Loose.minutes("None"))
        assertEquals(0, Loose.minutes("no commute, I work from home"))
        assertEquals(30, Loose.minutes("about 30 min"))
        assertEquals(60, Loose.minutes("an hour"))
        assertEquals(90, Loose.minutes("1h 30m"))
        assertEquals(30, Loose.minutes("half an hour"))
        assertEquals(30, Loose.minutes("normally 30 min"))
        assertEquals(90, Loose.minutes("1.5", defaultUnitHours = true))
        assertEquals(45, Loose.minutes("forty-five minutes"))
        assertEquals(true, Loose.yesNo("Yes, 9 to 5"))
        assertEquals(false, Loose.yesNo("nope"))
        assertEquals(EnergyPeak.EVENING, Loose.energy("late at night"))
        assertEquals("Mon–Fri", Loose.daysLabel((1..5).toSet()))
    }

    private suspend fun talk(vararg answers: String): Pair<ChatTurn, List<ChatMessage>> {
        val history = mutableListOf<ChatMessage>()
        var turn = ScriptedInterviewer.next(history, UserProfile(name = "Sam Lee"))
        history += ChatMessage(false, turn.reply)
        for (a in answers) {
            history += ChatMessage(true, a)
            turn = ScriptedInterviewer.next(history, UserProfile())
            history += ChatMessage(false, turn.reply)
        }
        return turn to history
    }

    @Test
    fun scriptedInterviewLearnsWholeRoutine() = runBlocking {
        val (first, _) = talk()
        assertTrue(first.reply.startsWith("Hi Sam!"))
        assertTrue(first.quickReplies.isNotEmpty())

        val (turn, _) = talk("6:30", "11pm", "yes", "9 to 5", "weekdays", "30 min", "morning", "2 hours", "4 hours", "45 min")
        assertTrue(turn.done)
        val p = UserProfile().merge(turn.draft!!)
        assertEquals(6 * 60 + 30, p.routine.wakeTime)
        assertEquals(23 * 60, p.routine.sleepTime)
        assertEquals(9 * 60, p.routine.workStart)
        assertEquals(17 * 60, p.routine.workEnd)
        assertEquals((1..5).toSet(), p.routine.workDays)
        assertEquals(30, p.routine.commuteMinutes)
        assertEquals(EnergyPeak.MORNING, p.preferences.peakEnergy)
        assertEquals(2.0, p.preferences.maxTaskHoursWorkday)
        assertEquals(4.0, p.preferences.maxTaskHoursFreeDay)
        assertEquals(45, p.preferences.focusBlockMinutes)
        assertEquals(listOf("Looks right", "Change something"), turn.quickReplies)
    }

    @Test
    fun scriptedInterviewSkipsWorkQuestionsAndRetriesBadAnswers() = runBlocking {
        val (retry, _) = talk("whenever I feel like it")
        assertFalse(retry.done)
        assertTrue("didn't catch" in retry.reply)

        val (turn, _) = talk("7", "midnight", "no", "evening", "1 hour", "3 hours", "25 min")
        assertTrue(turn.done)
        val p = UserProfile().merge(turn.draft!!)
        assertTrue(p.routine.workDays.isEmpty())
        assertEquals(0, p.routine.sleepTime)
        assertEquals(EnergyPeak.EVENING, p.preferences.peakEnergy)
        assertEquals(25, p.preferences.focusBlockMinutes)
    }

    @Test
    fun aiInterviewerSendsWholeConversation() = runBlocking {
        var sent = ""
        val http = HttpPost { _, _, body ->
            sent = body
            val content = """{"reply":"Nice! And when do you go to bed?","quickReplies":["22:00","23:00"],"done":false,
                "profile":{"wakeTime":"07:00","sleepTime":null,"summary":""}}"""
            200 to """{"choices":[{"finish_reason":"stop","message":{"content":${JsonPrimitive(content)}}}]}"""
        }
        val ai = AiInterviewer(AiPlanner(AiProvider.GROQ, "k", "", http))
        val turn = ai.next(listOf(ChatMessage(false, "Hi! When do you wake up?"), ChatMessage(true, "7ish")), UserProfile())

        val messages = PaceJson.parseToJsonElement(sent).jsonObject["messages"]!!.jsonArray
        assertEquals(listOf("system", "user", "assistant", "user"), messages.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        assertEquals("7ish", messages.last().jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("Nice! And when do you go to bed?", turn.reply)
        assertEquals("07:00", turn.draft?.wakeTime)
        assertEquals(listOf("22:00", "23:00"), turn.quickReplies)
    }

    @Test
    fun streakCountsConsecutiveDoneDays() {
        val d = LocalDate.of(2026, 11, 10)
        fun done(day: LocalDate) = PlannedSession("x$day", "g", "s", "t", "g", day.toString(), 600, 30, status = SessionStatus.DONE)
        val state = AppState(sessions = listOf(done(d.minusDays(1)), done(d.minusDays(2)), done(d.minusDays(4))))
        assertEquals(2, StateOps.streak(state, d))
        assertEquals(3, StateOps.streak(state.copy(sessions = state.sessions + done(d)), d))
        assertEquals(0, StateOps.streak(AppState(), d))
    }
}
