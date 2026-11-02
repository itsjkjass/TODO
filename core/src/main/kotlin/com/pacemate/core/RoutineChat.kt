package com.pacemate.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/** One bubble in the routine chat. */
@Serializable
data class ChatMessage(val fromUser: Boolean, val text: String)

/** What the interviewer says next, plus everything it has learned so far. */
data class ChatTurn(
    val reply: String,
    /** Routine fields learned so far (null fields = not known yet). */
    val draft: RoutineDraft?,
    /** Tappable suggested answers for the next question. */
    val quickReplies: List<String> = emptyList(),
    /** True when the interviewer has everything it needs. */
    val done: Boolean = false,
)

/** Learns the user's routine through a short back-and-forth conversation. */
interface RoutineInterviewer {
    suspend fun next(history: List<ChatMessage>, profile: UserProfile): ChatTurn
}

// ------------------------------------------------------------------ AI interviewer

@Serializable
private data class ChatTurnDto(
    val reply: String = "",
    val quickReplies: List<String> = emptyList(),
    val done: Boolean = false,
    val profile: RoutineDraft? = null,
)

/** The AI asks one friendly question at a time and returns structured data each turn. */
class AiInterviewer(private val planner: AiPlanner) : RoutineInterviewer {
    override suspend fun next(history: List<ChatMessage>, profile: UserProfile): ChatTurn {
        val system = """
            You are PaceMate, a warm, upbeat planning coach inside a to-do app. You are chatting with the user
            to learn their typical daily routine so the app can plan tasks around their life.

            Learn, roughly in this order, by asking ONE short, friendly question at a time (max ~25 words):
            1. wake-up time and bedtime
            2. whether they have fixed work/school hours; if yes the hours, the days, and commute length
            3. meals and any fixed commitments (gym, classes, school run, prayer, family time) with times and days
            4. when they have the most energy (morning / afternoon / evening)
            5. how much time per day they want to give their own tasks on work days and on free days
            6. how long they can focus before needing a break
            Skip anything they already told you. If an answer is vague, make a sensible assumption and say it briefly.
            React naturally to what they say (one short sentence) before the next question. Never lecture.

            Reply ONLY with a JSON object:
            {"reply":"your message","quickReplies":["2-4 short tappable answers to your question"],
             "done":false,
             "profile":{"wakeTime":"HH:MM or null","sleepTime":null,"workStart":null,"workEnd":null,
                        "workDays":[1,2,3,4,5] or null,"commuteMinutes":null,
                        "meals":[{"label":"Lunch","start":"13:00","durationMinutes":45,"days":[]}] or null,
                        "fixedBlocks":[{"label":"Gym","start":"19:00","durationMinutes":60,"days":[2,4]}] or null,
                        "focusBlockMinutes":null,"breakMinutes":null,"peakEnergy":"MORNING|AFTERNOON|EVENING or null",
                        "maxTaskHoursWorkday":null,"maxTaskHoursFreeDay":null,"restDays":null,"summary":""}}
            "profile" must contain EVERYTHING learned in the whole conversation so far (not just this turn).
            Times are 24h "HH:MM"; days are ISO numbers 1=Mon..7=Sun; someone without fixed hours has "workDays":[].
            When you know enough for all six topics, set "done":true, give a 1-2 sentence recap in "reply",
            fill "profile.summary" with that recap, and use quickReplies ["Looks right", "Change something"].
        """.trimIndent()

        val context = "Current saved settings (may be defaults): " + PaceJson.encodeToString(profile.routine) +
            "; user name: \"${profile.name}\"."
        val turns = buildList {
            add(false to "$context\nStart the conversation: greet me briefly and ask your first question.")
            history.forEach { add(!it.fromUser to it.text) }
        }
        val text = planner.chatTurns(system, turns)
        val dto = try {
            PaceJson.decodeFromString<ChatTurnDto>(AiPlanner.extractJson(text))
        } catch (e: Exception) {
            throw AiException("I couldn't read that reply. Please send your message again.", e)
        }
        if (dto.reply.isBlank()) throw AiException("The AI went quiet. Please send your message again.")
        return ChatTurn(dto.reply.trim(), dto.profile, dto.quickReplies.filter { it.isNotBlank() }.take(4), dto.done)
    }
}

// ------------------------------------------------------------------ offline interviewer

/**
 * A scripted interview for when no AI key is set. It understands loose answers
 * like "7am", "half past six", "9 to 5", "weekdays", "an hour", "none".
 */
object ScriptedInterviewer : RoutineInterviewer {

    private enum class Step { WAKE, SLEEP, HAS_WORK, WORK_HOURS, WORK_DAYS, COMMUTE, ENERGY, HOURS_WORKDAY, HOURS_FREEDAY, FOCUS, DONE }

    private data class Question(val text: String, val replies: List<String>, val retry: String)

    private val questions = mapOf(
        Step.WAKE to Question("What time do you usually wake up?", listOf("6:00", "7:00", "8:00", "9:00"), "Sorry, I didn't catch a time. Something like \"7am\" or \"6:30\"?"),
        Step.SLEEP to Question("And when do you usually go to sleep?", listOf("22:30", "23:00", "00:00", "01:00"), "What time is bedtime? For example \"11pm\"."),
        Step.HAS_WORK to Question("Do you have fixed work or class hours?", listOf("Yes", "No"), "Just yes or no: do you have fixed work or class hours?"),
        Step.WORK_HOURS to Question("What are those hours?", listOf("9 to 5", "9 to 6", "10 to 7", "8 to 4"), "Tell me start and end, like \"9 to 5\" or \"8:30-17:00\"."),
        Step.WORK_DAYS to Question("Which days?", listOf("Mon–Fri", "Mon–Sat", "Every day"), "Which days? For example \"weekdays\" or \"Mon, Wed, Fri\"."),
        Step.COMMUTE to Question("How long is your commute, one way?", listOf("None", "15 min", "30 min", "1 hour"), "Roughly how many minutes? \"None\" is fine too."),
        Step.ENERGY to Question("When do you usually have the most energy?", listOf("Morning", "Afternoon", "Evening"), "Morning, afternoon or evening?"),
        Step.HOURS_WORKDAY to Question("On work days, how much time do you want to spend on your own tasks?", listOf("1 hour", "2 hours", "3 hours"), "How many hours? \"1.5 hours\" works."),
        Step.HOURS_FREEDAY to Question("And on free days?", listOf("2 hours", "4 hours", "6 hours"), "How many hours on a free day?"),
        Step.FOCUS to Question("Last one: how long can you focus before you need a break?", listOf("25 min", "45 min", "60 min", "90 min"), "How many minutes of focus? For example \"45 min\"."),
    )

    override suspend fun next(history: List<ChatMessage>, profile: UserProfile): ChatTurn {
        val (step, draft, failed) = replay(history)
        if (step == Step.DONE) {
            val p = profile.merge(draft)
            return ChatTurn(
                reply = "Got it! " + summarize(p) + " Does that look right?",
                draft = draft.copy(summary = summarize(p)),
                quickReplies = listOf("Looks right", "Change something"),
                done = true,
            )
        }
        val q = questions.getValue(step)
        val text = when {
            history.isEmpty() -> "Hi${if (profile.name.isBlank()) "" else " ${profile.name.trim().substringBefore(' ')}"}! I'll ask a few quick questions so I can plan around your day. ${q.text}"
            failed -> q.retry
            else -> acknowledgement(step) + q.text
        }
        return ChatTurn(text, draft, q.replies, false)
    }

    private fun acknowledgement(next: Step) = when (next) {
        Step.SLEEP, Step.WORK_DAYS, Step.HOURS_FREEDAY -> ""
        Step.ENERGY -> "Thanks, noted. "
        Step.FOCUS -> "Perfect. "
        else -> "Great. "
    }

    /** Re-reads the user's answers in order. Returns the step to ask next, what was learned, and whether the last answer failed. */
    private fun replay(history: List<ChatMessage>): Triple<Step, RoutineDraft, Boolean> {
        var step = Step.WAKE
        var draft = RoutineDraft()
        var failed = false
        for (answer in history.filter { it.fromUser }.map { it.text }) {
            if (step == Step.DONE) break
            val result = handle(step, answer, draft)
            if (result == null) {
                failed = true
            } else {
                failed = false
                draft = result.first
                step = result.second
            }
        }
        return Triple(step, draft, failed)
    }

    private fun handle(step: Step, answer: String, d: RoutineDraft): Pair<RoutineDraft, Step>? {
        val a = answer.trim()
        return when (step) {
            Step.WAKE -> Loose.time(a)?.let { d.copy(wakeTime = Times.format(it)) to Step.SLEEP }
            Step.SLEEP -> Loose.time(a, preferEvening = true)?.let { d.copy(sleepTime = Times.format(it)) to Step.HAS_WORK }
            Step.HAS_WORK -> when (Loose.yesNo(a)) {
                true -> d to Step.WORK_HOURS
                false -> d.copy(workDays = emptyList(), commuteMinutes = 0) to Step.ENERGY
                null -> Loose.range(a)?.let { (s, e) -> d.copy(workStart = Times.format(s), workEnd = Times.format(e)) to Step.WORK_DAYS }
            }
            Step.WORK_HOURS -> Loose.range(a)?.let { (s, e) -> d.copy(workStart = Times.format(s), workEnd = Times.format(e)) to Step.WORK_DAYS }
            Step.WORK_DAYS -> Loose.days(a)?.let { d.copy(workDays = it.sorted()) to Step.COMMUTE }
            Step.COMMUTE -> Loose.minutes(a)?.let { d.copy(commuteMinutes = it.coerceIn(0, 240)) to Step.ENERGY }
            Step.ENERGY -> Loose.energy(a)?.let { d.copy(peakEnergy = it.name) to Step.HOURS_WORKDAY }
            Step.HOURS_WORKDAY -> Loose.minutes(a, defaultUnitHours = true)?.let { d.copy(maxTaskHoursWorkday = it / 60.0) to Step.HOURS_FREEDAY }
            Step.HOURS_FREEDAY -> Loose.minutes(a, defaultUnitHours = true)?.let { d.copy(maxTaskHoursFreeDay = it / 60.0) to Step.FOCUS }
            Step.FOCUS -> Loose.minutes(a)?.let { d.copy(focusBlockMinutes = it.coerceIn(15, 180)) to Step.DONE }
            Step.DONE -> d to Step.DONE
        }
    }

    fun summarize(p: UserProfile): String {
        val r = p.routine
        val prefs = p.preferences
        val work = if (r.workDays.isEmpty()) "no fixed work hours"
        else "work ${Times.format(r.workStart)}–${Times.format(r.workEnd)} ${Loose.daysLabel(r.workDays)}" +
            if (r.commuteMinutes > 0) " (+${r.commuteMinutes} min commute)" else ""
        return "You're up at ${Times.format(r.wakeTime)}, asleep by ${Times.format(r.sleepTime)}, $work, " +
            "sharpest in the ${prefs.peakEnergy.name.lowercase()}, with about ${hours(prefs.maxTaskHoursWorkday)} for your own tasks on work days " +
            "and ${hours(prefs.maxTaskHoursFreeDay)} on free days, in ${prefs.focusBlockMinutes}-minute focus blocks."
    }

    private fun hours(h: Double) = Times.duration((h * 60).toInt())
}

/** Forgiving parsers for chat answers. */
object Loose {
    private val words = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "forty-five" to 45, "fifty" to 50, "sixty" to 60, "ninety" to 90,
    )

    private fun normalize(s: String): String {
        var t = s.lowercase().replace("–", "-").replace("—", "-")
        t = t.replace(Regex("\\ban? hour\\b"), "1 hour")
        // Longest words first so "forty-five" wins over "forty".
        words.entries.sortedByDescending { it.key.length }.forEach { (w, n) -> t = t.replace(Regex("\\b$w\\b"), n.toString()) }
        return t
    }

    /** "7", "7am", "7:30", "7.30 pm", "19:00", "noon", "midnight", "half past 6". */
    fun time(s: String, preferEvening: Boolean = false): Int? {
        val t = normalize(s)
        if ("midnight" in t) return 0
        if ("noon" in t || "midday" in t) return 12 * 60
        Regex("half past (\\d{1,2})").find(t)?.let { m ->
            return adjust(m.groupValues[1].toInt(), 30, t, preferEvening)
        }
        val m = Regex("(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?").find(t) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toIntOrNull() ?: 0
        if (h > 24 || min > 59) return null
        val suffix = m.groupValues[3]
        return when {
            suffix.startsWith("p") -> ((h % 12) + 12) * 60 + min
            suffix.startsWith("a") -> (h % 12) * 60 + min
            else -> adjust(h, min, t, preferEvening)
        }
    }

    /**
     * Resolves an hour without am/pm. For bedtimes ([preferEvening]) "11" means 23:00,
     * "12" means midnight and "1"-"4" mean after midnight.
     */
    private fun adjust(h: Int, min: Int, text: String, preferEvening: Boolean): Int {
        val evening = "evening" in text || "night" in text
        val hour = when {
            h == 24 -> 0
            evening && h in 1..11 -> h + 12
            preferEvening && h in 5..11 -> h + 12
            preferEvening && h == 12 -> 0
            else -> h
        }
        return (hour % 24) * 60 + min
    }

    /** "9 to 5", "9-17", "8:30 - 16:30", "9am to 6pm". End before start means afternoon (5 -> 17). */
    fun range(s: String): Pair<Int, Int>? {
        val t = normalize(s)
        val parts = t.split(Regex("\\s*(?:-|to|until|till)\\s*")).filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val start = time(parts[0]) ?: return null
        var end = time(parts[1]) ?: return null
        if (end <= start && end < 12 * 60) end += 12 * 60
        if (end <= start) return null
        return start to end
    }

    /** "weekdays", "mon-fri", "mon to sat", "every day", "mon, wed, fri". */
    fun days(s: String): Set<Int>? {
        val t = normalize(s)
        if (listOf("every day", "everyday", "daily", "all days", "7 days").any { it in t }) return (1..7).toSet()
        if ("weekday" in t) return (1..5).toSet()
        if ("weekend" in t) return setOf(6, 7)
        val names = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        Regex("(mon|tue|wed|thu|fri|sat|sun)[a-z]*\\s*(?:-|to|through|till|until)\\s*(mon|tue|wed|thu|fri|sat|sun)").find(t)?.let { m ->
            val a = names.indexOf(m.groupValues[1]) + 1
            val b = names.indexOf(m.groupValues[2]) + 1
            return if (a <= b) (a..b).toSet() else ((a..7) + (1..b)).toSet()
        }
        val found = names.mapIndexedNotNull { i, n -> if (Regex("\\b$n").containsMatchIn(t)) i + 1 else null }.toSet()
        return found.ifEmpty { null }
    }

    fun daysLabel(days: Set<Int>): String {
        val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val sorted = days.sorted()
        return when {
            sorted == (1..7).toList() -> "every day"
            sorted.size >= 3 && sorted.zipWithNext().all { (a, b) -> b == a + 1 } -> "${names[sorted.first() - 1]}–${names[sorted.last() - 1]}"
            else -> sorted.joinToString(", ") { names[it - 1] }
        }
    }

    /** "none" -> 0, "30 min", "1 hour", "1.5 hours", "1h 30m", "90". Bare numbers are minutes unless [defaultUnitHours]. */
    fun minutes(s: String, defaultUnitHours: Boolean = false): Int? {
        val raw = s.lowercase()
        if ("half an hour" in raw || "half hour" in raw || "1/2 hour" in raw) return 30
        val t = normalize(s)
        if (Regex("^\\s*(none|no|zero|nothing|nil|wfh|remote|work from home)\\b").containsMatchIn(t)) return 0
        var total = 0.0
        var matched = false
        Regex("(\\d+(?:\\.\\d+)?)\\s*(h|hr|hrs|hour|hours)\\b").findAll(t).forEach { total += it.groupValues[1].toDouble() * 60; matched = true }
        Regex("(\\d+)\\s*(m|min|mins|minute|minutes)\\b").findAll(t).forEach { total += it.groupValues[1].toDouble(); matched = true }
        if (!matched) {
            val n = Regex("(\\d+(?:\\.\\d+)?)").find(t)?.groupValues?.get(1)?.toDouble() ?: return null
            total = if (defaultUnitHours && n <= 16) n * 60 else n
        }
        return total.toInt()
    }

    fun yesNo(s: String): Boolean? {
        val t = normalize(s).trim()
        return when {
            Regex("^(yes|yeah|yep|yup|sure|i do|correct|y)\\b").containsMatchIn(t) -> true
            Regex("^(no|nope|nah|not really|i don't|i dont|n)\\b").containsMatchIn(t) -> false
            else -> null
        }
    }

    fun energy(s: String): EnergyPeak? {
        val t = normalize(s)
        return when {
            "morning" in t || "early" in t || t.startsWith("am") -> EnergyPeak.MORNING
            "afternoon" in t || "midday" in t || "lunch" in t -> EnergyPeak.AFTERNOON
            "evening" in t || "night" in t || "late" in t -> EnergyPeak.EVENING
            else -> null
        }
    }
}
