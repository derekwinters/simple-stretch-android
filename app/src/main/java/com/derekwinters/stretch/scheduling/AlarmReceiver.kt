package com.derekwinters.stretch.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.data.days
import com.derekwinters.stretch.notifications.Notifications
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Receives a reminder's alarm, decides whether to notify, and arms the next occurrence. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        val triggerAt = intent.getLongExtra(ReminderScheduler.EXTRA_TRIGGER_AT, System.currentTimeMillis())
        if (reminderId < 0) return

        val app = context.applicationContext as StretchApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                handle(app, reminderId, triggerAt)
                ReminderScheduler.onAlarmHandled(app, reminderId)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: StretchApp, reminderId: Long, triggerAt: Long) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // The same occurrence can be delivered twice if a reschedule re-armed it; notify once.
        if (prefs.getLong(FIRED_PREFIX + reminderId, -1L) == triggerAt) return
        prefs.edit().putLong(FIRED_PREFIX + reminderId, triggerAt).apply()

        val dao = app.database.scheduleDao()
        val reminder = dao.getReminder(reminderId) ?: return
        val schedule = dao.getSchedule(reminder.reminder.scheduleId) ?: return
        if (!schedule.enabled) return

        val date = Instant.ofEpochMilli(triggerAt).atZone(ZoneId.systemDefault()).toLocalDate()
        // SKIP-003: a skipped date never produces a notification, even if an alarm was already set.
        if (app.database.skipDao().isSkipped(date.toEpochDay())) return
        if (date.dayOfWeek !in schedule.days) return
        // Don't nag about something that was due hours ago (e.g. delivery delayed by Doze).
        if (System.currentTimeMillis() - triggerAt > STALE_AFTER_MILLIS) return

        Notifications.showReminder(app, reminderId, schedule.name, reminder.stretches)
    }

    private companion object {
        const val PREFS = "alarm_receiver"
        const val FIRED_PREFIX = "fired_"
        const val STALE_AFTER_MILLIS = 2 * 60 * 60 * 1000L
    }
}

/** Re-arms every alarm after reboot, clock/time-zone changes, app update or permission change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val app = context.applicationContext as StretchApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                ReminderScheduler.rescheduleAll(app)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED, // "android.intent.action.TIME_SET"
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
