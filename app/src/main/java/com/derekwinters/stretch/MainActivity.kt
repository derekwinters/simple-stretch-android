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

    /** NOTIF-002/003: open the stretch session and clear the notification it came from. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_SESSION) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (notificationId != Int.MIN_VALUE) NotificationManagerCompat.from(this).cancel(notificationId)
        pendingRoute = Routes.SESSION
    }

    override fun onResume() {
        super.onResume()
        // The exact-alarm permission may have changed while we were in Settings.
        lifecycleScope.launch { ReminderScheduler.rescheduleAll(this@MainActivity) }
    }

    companion object {
        const val ACTION_OPEN_SESSION = "com.derekwinters.stretch.action.OPEN_SESSION"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}
