package com.derekwinters.stretch.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.derekwinters.stretch.StretchApp
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Handles the "Done" and "Skip rest of today" notification buttons. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) NotificationManagerCompat.from(context).cancel(notificationId)

        when (intent.action) {
            ACTION_DONE -> Unit
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
        const val ACTION_DONE = "com.derekwinters.stretch.action.DONE"
        const val ACTION_SKIP_TODAY = "com.derekwinters.stretch.action.SKIP_TODAY"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}
