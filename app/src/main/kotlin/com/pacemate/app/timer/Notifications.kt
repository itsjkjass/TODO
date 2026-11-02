package com.pacemate.app.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pacemate.app.R
import com.pacemate.app.ui.MainActivity
import com.pacemate.core.PlannedSession
import com.pacemate.core.Times

object Notifications {
    const val CHANNEL_RUNNING = "timer_running"
    const val CHANNEL_TIME_UP = "timer_time_up"
    const val CHANNEL_REMINDERS = "reminders"

    const val ID_RUNNING = 1001
    const val ID_TIME_UP = 1002
    private const val ID_REMINDER_BASE = 20_000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val running = NotificationChannel(CHANNEL_RUNNING, "Running task timer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows the countdown for the task you are working on"
            setShowBadge(false)
        }
        val timeUp = NotificationChannel(CHANNEL_TIME_UP, "Task time is over", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts you when the time planned for a task has run out"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        val reminders = NotificationChannel(CHANNEL_REMINDERS, "Upcoming task reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A gentle heads-up shortly before a planned task starts"
        }
        nm.createNotificationChannels(listOf(running, timeUp, reminders))
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(context: Context, id: Int, notification: Notification) {
        if (!canPost(context)) return
        context.getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, action: String, requestCode: Int, sessionId: String? = null): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, TimerReceiver::class.java).setAction(action).apply {
                sessionId?.let { putExtra(TimerReceiver.EXTRA_SESSION_ID, it) }
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Ongoing notification with a live countdown (or overtime count-up once the time is over). */
    fun showRunning(context: Context, session: PlannedSession, startedAt: Long, plannedEnd: Long) {
        val now = System.currentTimeMillis()
        val overtime = now >= plannedEnd
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(if (overtime) "Over time: ${session.title}" else "Working on: ${session.title}")
            .setContentText(
                if (overtime) "Planned ${Times.duration(session.durationMinutes)} is used up. Wrap up or add time."
                else "${session.goalTitle} · planned ${Times.duration(session.durationMinutes)}",
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setUsesChronometer(true)
            .setChronometerCountDown(!overtime)
            .setWhen(plannedEnd)
            .setShowWhen(true)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "Done", action(context, TimerReceiver.ACTION_DONE, 1))
            .addAction(0, "+10 min", action(context, TimerReceiver.ACTION_EXTEND, 2))
            .addAction(0, "Stop", action(context, TimerReceiver.ACTION_STOP, 3))
        post(context, ID_RUNNING, builder.build())
    }

    fun showTimeUp(context: Context, session: PlannedSession, loggedMinutes: Long) {
        val n = NotificationCompat.Builder(context, CHANNEL_TIME_UP)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Time's up: ${session.title}")
            .setContentText("You planned ${Times.duration(session.durationMinutes)} and have logged ${loggedMinutes} min. Finish up, take a break, or add 10 minutes.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "You planned ${Times.duration(session.durationMinutes)} for \"${session.title}\" " +
                        "and have logged $loggedMinutes min.\nIt's fine to stop here - the rest will be planned for later. " +
                        "Or add 10 minutes if you're almost done.",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "Done", action(context, TimerReceiver.ACTION_DONE, 4))
            .addAction(0, "+10 min", action(context, TimerReceiver.ACTION_EXTEND, 5))
            .addAction(0, "Stop", action(context, TimerReceiver.ACTION_STOP, 6))
            .build()
        post(context, ID_TIME_UP, n)
    }

    fun showReminder(context: Context, session: PlannedSession) {
        val id = ID_REMINDER_BASE + (session.id.hashCode() and 0xFFFF)
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Up next at ${Times.format(session.startMinute)}: ${session.title}")
            .setContentText("${session.goalTitle} · ${Times.duration(session.durationMinutes)}")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "Start timer", action(context, TimerReceiver.ACTION_START, id, session.id))
            .build()
        post(context, id, n)
    }

    fun cancelReminder(context: Context, sessionId: String) =
        cancel(context, ID_REMINDER_BASE + (sessionId.hashCode() and 0xFFFF))
}
