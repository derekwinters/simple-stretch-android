package com.derekwinters.stretch.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.data.Schedule
import com.derekwinters.stretch.data.Stretch
import com.derekwinters.stretch.data.days
import com.derekwinters.stretch.data.isRepeating
import com.derekwinters.stretch.goals.GoalMath
import com.derekwinters.stretch.notifications.Notifications
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Receives a reminder's alarm (scheduled or snoozed), decides whether to notify, and, for a
 * scheduled alarm, arms the next occurrence.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ReminderScheduler.ACTION_FIRE && action != ReminderScheduler.ACTION_SNOOZE_FIRE) return
        val key = when {
            intent.hasExtra(ReminderScheduler.EXTRA_ALARM_KEY) ->
                intent.getLongExtra(ReminderScheduler.EXTRA_ALARM_KEY, 0L)
            // Alarms armed by version 1 carry only a reminder id.
            else -> intent.getLongExtra(ReminderScheduler.EXTRA_LEGACY_REMINDER_ID, 0L)
        }
        val triggerAt = intent.getLongExtra(ReminderScheduler.EXTRA_TRIGGER_AT, System.currentTimeMillis())
        if (key == 0L) return
        val snoozed = action == ReminderScheduler.ACTION_SNOOZE_FIRE

        val app = context.applicationContext as StretchApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                handle(app, key, triggerAt, snoozed)
                // SCHED-015: a snooze is not one of the scheduler's alarms; nothing to re-arm.
                if (!snoozed) ReminderScheduler.onAlarmHandled(app, key)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: StretchApp, key: Long, triggerAt: Long, snoozed: Boolean) {
        if (!snoozed) {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // NOTIF-007: the same occurrence can be delivered twice if a reschedule re-armed it.
            if (prefs.getLong(FIRED_PREFIX + key, -1L) == triggerAt) return
            prefs.edit().putLong(FIRED_PREFIX + key, triggerAt).apply()
        }

        val dao = app.database.scheduleDao()
        val schedule: Schedule
        val stretches: List<Stretch>
        val reminderId = AlarmKeys.reminderId(key)
        if (reminderId != null) {
            val reminder = dao.getReminder(reminderId) ?: return
            schedule = dao.getSchedule(reminder.reminder.scheduleId) ?: return
            if (schedule.isRepeating) return
            stretches = reminder.stretches
        } else {
            val scheduleId = AlarmKeys.scheduleId(key) ?: return
            schedule = dao.getSchedule(scheduleId) ?: return
            // SCHED-013: the schedule must still be repeating.
            if (!schedule.isRepeating) return
            stretches = emptyList()
        }
        if (!schedule.enabled) return

        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(triggerAt).atZone(zone).toLocalDate()
        // SKIP-003: a skipped date never produces a notification, even if an alarm was already
        // set - and that includes a snooze (SCHED-015).
        if (app.database.skipDao().isSkipped(date.toEpochDay())) return
        // SCHED-003 invariant, re-checked at delivery.
        if (date.dayOfWeek !in schedule.days) return
        // NOTIF-006: don't nag about something that was due hours ago (e.g. delayed by Doze).
        if (System.currentTimeMillis() - triggerAt > STALE_AFTER_MILLIS) return

        // NOTIF-010: once every goal for the day is met, no reminder (set time, repeating or
        // snoozed) is shown. The caller still re-arms the next alarm, so the schedule keeps
        // running. With no goals, allGoalsMet is false and reminders behave as before.
        val progress = app.repository.goalProgressOn(date, zone)
        if (GoalMath.allGoalsMet(progress)) return

        // NOTIF-001: a reminder without stretches lists today's unmet goals.
        val goalLines = if (stretches.isEmpty()) GoalMath.reminderSummary(progress) else null
        Notifications.showReminder(app, key, schedule.name, stretches, goalLines)
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
