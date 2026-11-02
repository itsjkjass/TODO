package com.pacemate.app.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pacemate.app.PaceMateApp
import com.pacemate.core.SessionStatus
import com.pacemate.core.StateOps
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The task logger. Starting a task records the start time and arms an alarm
 * for the moment the planned minutes run out; the alarm posts a "time's up"
 * notification even if the app is closed or the phone is idle.
 */
object TimerController {
    private const val RC_TIME_UP = 500

    private val repo get() = PaceMateApp.instance.repository

    fun start(context: Context, sessionId: String) {
        val state = repo.update { StateOps.startTimer(it, sessionId, System.currentTimeMillis()) }
        Notifications.cancel(context, Notifications.ID_TIME_UP)
        Notifications.cancelReminder(context, sessionId)
        sync(context, state.activeTimer != null)
    }

    fun stop(context: Context, markDone: Boolean) {
        repo.update { StateOps.stopTimer(it, System.currentTimeMillis(), markDone) }
        cancelAlarm(context)
        Notifications.cancel(context, Notifications.ID_RUNNING)
        Notifications.cancel(context, Notifications.ID_TIME_UP)
    }

    fun extend(context: Context, minutes: Int = 10) {
        repo.update { StateOps.extendTimer(it, minutes, System.currentTimeMillis()) }
        Notifications.cancel(context, Notifications.ID_TIME_UP)
        sync(context, true)
    }

    fun markDone(context: Context, sessionId: String) {
        if (repo.current.activeTimer?.sessionId == sessionId) stop(context, markDone = true)
        else repo.update { StateOps.markSessionDone(it, sessionId) }
    }

    /** Re-arms alarm + ongoing notification from the stored state (after boot, app start, extend...). */
    fun sync(context: Context, hasTimer: Boolean = repo.current.activeTimer != null) {
        val state = repo.current
        val t = state.activeTimer
        if (!hasTimer || t == null) {
            cancelAlarm(context)
            Notifications.cancel(context, Notifications.ID_RUNNING)
            return
        }
        val session = state.sessions.firstOrNull { it.id == t.sessionId } ?: return
        Notifications.showRunning(context, session, t.startedAtEpochMs, t.plannedEndEpochMs)
        if (t.plannedEndEpochMs > System.currentTimeMillis()) {
            scheduleExact(context, t.plannedEndEpochMs, timeUpIntent(context))
        }
    }

    /** Called by the alarm when the planned time is over. */
    fun onTimeUp(context: Context) {
        val state = repo.current
        val t = state.activeTimer ?: return
        // A stale alarm (timer was extended) is ignored; the newer one will fire.
        if (System.currentTimeMillis() < t.plannedEndEpochMs - 5_000) return
        val session = state.sessions.firstOrNull { it.id == t.sessionId } ?: return
        val logged = (session.loggedSeconds + (System.currentTimeMillis() - t.startedAtEpochMs) / 1000) / 60
        Notifications.showTimeUp(context, session, logged)
        Notifications.showRunning(context, session, t.startedAtEpochMs, t.plannedEndEpochMs)
    }

    private fun timeUpIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, RC_TIME_UP,
        Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_TIME_UP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(timeUpIntent(context))
    }

    fun scheduleExact(context: Context, atMs: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        }
    }

    // ---------- start-of-task reminders ----------

    private const val REMINDER_LEAD_MINUTES = 2L

    /** Schedules a heads-up shortly before each planned session in the next ~36 hours. */
    fun rescheduleReminders(context: Context) {
        val app = PaceMateApp.instance
        val am = context.getSystemService(AlarmManager::class.java)
        for (code in app.settings.scheduledReminderCodes) {
            am.cancel(reminderIntent(context, code.toInt(), null))
        }
        if (!app.settings.startReminders) {
            app.settings.scheduledReminderCodes = emptySet()
            return
        }
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val until = now + 36 * 3600_000L
        val codes = mutableSetOf<String>()
        val activeId = app.repository.current.activeTimer?.sessionId
        for (s in app.repository.current.sessions) {
            if (s.status != SessionStatus.PLANNED || s.id == activeId) continue
            val start = LocalDateTime.of(LocalDate.parse(s.date), java.time.LocalTime.MIN)
                .plusMinutes(s.startMinute.toLong())
                .minusMinutes(REMINDER_LEAD_MINUTES)
                .atZone(zone).toInstant().toEpochMilli()
            if (start <= now || start > until) continue
            val code = 10_000 + (s.id.hashCode() and 0xFFFF)
            scheduleExact(context, start, reminderIntent(context, code, s.id))
            codes += code.toString()
        }
        app.settings.scheduledReminderCodes = codes
    }

    private fun reminderIntent(context: Context, code: Int, sessionId: String?): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_REMIND).apply {
            sessionId?.let { putExtra(TimerReceiver.EXTRA_SESSION_ID, it) }
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
