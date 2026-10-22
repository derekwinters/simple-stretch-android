package com.derekwinters.stretch.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.derekwinters.stretch.scheduling.ReminderScheduler

/**
 * Handles the "Snooze 5 min" notification button (NOTIF-008). Any other action, including the
 * retired "Skip today" one on notifications posted before it was removed, does nothing and leaves
 * the notification in place (NOTIF-004 retired, NOTIF-009 invariant).
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ReminderActions.isHandled(intent.action)) return

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (notificationId != Int.MIN_VALUE) NotificationManagerCompat.from(context).cancel(notificationId)

        when (intent.action) {
            ACTION_SNOOZE -> {
                val key = intent.getLongExtra(EXTRA_ALARM_KEY, 0L)
                // NOTIF-008 invariant: the re-post keeps the original scheduled time.
                val scheduledAt = if (intent.hasExtra(EXTRA_SCHEDULED_AT)) {
                    intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L)
                } else {
                    null
                }
                // SCHED-015: a one-shot alarm; the schedule's own alarm is left alone.
                if (key != 0L) ReminderScheduler.snooze(context.applicationContext, key, scheduledAt)
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.derekwinters.stretch.action.SNOOZE"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val EXTRA_ALARM_KEY = "alarmKey"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
    }
}
