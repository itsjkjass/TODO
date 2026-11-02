package com.pacemate.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pacemate.app.PaceMateApp
import com.pacemate.core.SessionStatus

/** Handles the timer alarm, reminder alarms and notification buttons. */
class TimerReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TIME_UP = "com.pacemate.app.TIME_UP"
        const val ACTION_DONE = "com.pacemate.app.DONE"
        const val ACTION_EXTEND = "com.pacemate.app.EXTEND"
        const val ACTION_STOP = "com.pacemate.app.STOP"
        const val ACTION_REMIND = "com.pacemate.app.REMIND"
        const val ACTION_START = "com.pacemate.app.START"
        const val EXTRA_SESSION_ID = "session_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TIME_UP -> TimerController.onTimeUp(context)
            ACTION_DONE -> TimerController.stop(context, markDone = true)
            ACTION_EXTEND -> TimerController.extend(context, 10)
            ACTION_STOP -> TimerController.stop(context, markDone = false)
            ACTION_REMIND -> {
                val id = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
                val state = PaceMateApp.instance.repository.current
                val session = state.sessions.firstOrNull { it.id == id && it.status == SessionStatus.PLANNED } ?: return
                if (state.activeTimer?.sessionId == id) return
                Notifications.showReminder(context, session)
            }
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
                TimerController.start(context, id)
            }
        }
    }
}

/** Alarms are cleared on reboot; put the timer and reminders back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            TimerController.sync(context)
            if (PaceMateApp.instance.repository.current.activeTimer?.let { System.currentTimeMillis() >= it.plannedEndEpochMs } == true) {
                TimerController.onTimeUp(context)
            }
            TimerController.rescheduleReminders(context)
        }
    }
}
