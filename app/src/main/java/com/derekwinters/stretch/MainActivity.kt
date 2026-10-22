package com.derekwinters.stretch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.derekwinters.stretch.scheduling.ReminderScheduler
import com.derekwinters.stretch.ui.Routes
import com.derekwinters.stretch.ui.StretchNavHost
import com.derekwinters.stretch.ui.theme.StretchTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** A screen to open once the UI is up (set from a notification intent), or null. */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // On recreation (e.g. rotation) the launch intent was already handled.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            StretchTheme {
                StretchNavHost(
                    pendingRoute = pendingRoute,
                    onPendingRouteHandled = { pendingRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * NOTIF-002/003: open the stretch session and clear the notification it came from.
     * WIDGET-007: the widget opens home, closing any screen above it.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_HOME) {
            pendingRoute = Routes.HOME
            return
        }
        if (intent?.action != ACTION_OPEN_SESSION) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (notificationId != Int.MIN_VALUE) NotificationManagerCompat.from(this).cancel(notificationId)
        // SESS-001: a reminder that names stretches opens the session with them as its own section.
        val stretchIds = intent.getLongArrayExtra(EXTRA_STRETCH_IDS)?.toList().orEmpty()
        val scheduleName = intent.getStringExtra(EXTRA_SCHEDULE_NAME)
        val scheduledAt = intent.getLongExtra(EXTRA_SCHEDULED_AT, -1L)
        pendingRoute = if (stretchIds.isNotEmpty() && scheduleName != null && scheduledAt >= 0) {
            Routes.session(stretchIds, scheduleName, scheduledAt)
        } else {
            Routes.session()
        }
    }

    override fun onResume() {
        super.onResume()
        // The exact-alarm permission may have changed while we were in Settings.
        lifecycleScope.launch { ReminderScheduler.rescheduleAll(this@MainActivity) }
    }

    companion object {
        const val ACTION_OPEN_SESSION = "com.derekwinters.stretch.action.OPEN_SESSION"
        const val ACTION_OPEN_HOME = "com.derekwinters.stretch.action.OPEN_HOME"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val EXTRA_STRETCH_IDS = "stretchIds"
        const val EXTRA_SCHEDULE_NAME = "scheduleName"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
    }
}
