package com.derekwinters.stretch.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.scheduling.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Handles the "Snooze 5 min" and "Skip today" notification buttons (NOTIF-004, NOTIF-008). */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (notificationId != Int.MIN_VALUE) NotificationManagerCompat.from(context).cancel(notificationId)

        when (intent.action) {
            ACTION_SNOOZE -> {
                val key = intent.getLongExtra(EXTRA_ALARM_KEY, 0L)
                // SCHED-015: a one-shot alarm; the schedule's own alarm is left alone.
                if (key != 0L) ReminderScheduler.snooze(context.applicationContext, key)
            }
            ACTION_SKIP_TODAY -> {
                val app = context.applicationContext as StretchApp
                val pending = goAsync()
                app.appScope.launch {
                    try {
                        app.repository.skipDate(LocalDate.now())
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.derekwinters.stretch.action.SNOOZE"
        const val ACTION_SKIP_TODAY = "com.derekwinters.stretch.action.SKIP_TODAY"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val EXTRA_ALARM_KEY = "alarmKey"
    }
}
