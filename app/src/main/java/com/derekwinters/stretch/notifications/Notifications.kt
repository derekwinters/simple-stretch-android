package com.derekwinters.stretch.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.derekwinters.stretch.MainActivity
import com.derekwinters.stretch.R
import com.derekwinters.stretch.data.Stretch
import com.derekwinters.stretch.goals.GoalMath
import com.derekwinters.stretch.scheduling.AlarmKeys

object Notifications {
    const val CHANNEL_ID = "stretch_reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_description)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * NOTIF-001..004, NOTIF-008, NOTIF-009: stretch names and instructions (or today's unmet
     * goals); tapping or "Start" opens the stretch session; "Snooze 5 min"; "Skip today".
     * Dismissing just this one is a swipe (Android shows at most three buttons).
     *
     * [key] is the alarm key (SCHED-005); it is also the notification id. [goalLines] is used
     * only when [stretches] is empty: null means no goals (generic text).
     */
    fun showReminder(
        context: Context,
        key: Long,
        scheduleName: String,
        stretches: List<Stretch>,
        goalLines: List<String>?,
    ) {
        if (!canPost(context)) return
        val notificationId = AlarmKeys.requestCode(key)

        val title = if (stretches.isEmpty()) {
            context.getString(R.string.notification_generic_title)
        } else {
            stretches.joinToString(", ") { it.name }
        }
        val body = when {
            stretches.isNotEmpty() -> stretches.joinToString("\n\n") { s ->
                buildString {
                    append(s.name)
                    s.durationSeconds?.let { append(" (").append(formatDuration(it)).append(")") }
                    if (s.description.isNotBlank()) append(": ").append(s.description)
                }
            }
            goalLines == null -> context.getString(R.string.notification_generic_body)
            goalLines == listOf(GoalMath.ALL_DONE) -> GoalMath.ALL_DONE
            else -> context.getString(R.string.notification_goals_header) + "\n" + goalLines.joinToString("\n")
        }
        val shortText = when {
            stretches.size == 1 -> stretches.first().description.ifBlank { scheduleName }
            stretches.isEmpty() && goalLines != null -> goalLines.joinToString(", ")
            else -> scheduleName
        }

        val openSession = sessionIntent(context, notificationId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(shortText)
            .setSubText(scheduleName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openSession)
            .addAction(0, context.getString(R.string.action_start), openSession)
            .addAction(
                0,
                context.getString(R.string.action_snooze),
                actionIntent(context, NotificationActionReceiver.ACTION_SNOOZE, notificationId, key),
            )
            .addAction(
                0,
                context.getString(R.string.action_skip_today),
                actionIntent(context, NotificationActionReceiver.ACTION_SKIP_TODAY, notificationId, key),
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing else to do.
        }
    }

    /**
     * NOTIF-002/003: opens [MainActivity] on the session screen. The activity cancels the
     * notification, since an action's activity intent does not auto-cancel it.
     */
    private fun sessionIntent(context: Context, notificationId: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_SESSION)
                .putExtra(MainActivity.EXTRA_NOTIFICATION_ID, notificationId)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun actionIntent(context: Context, action: String, notificationId: Int, key: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(action)
                .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(NotificationActionReceiver.EXTRA_ALARM_KEY, key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun formatDuration(seconds: Int): String =
        if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60} min"
        else if (seconds >= 60) "${seconds / 60} min ${seconds % 60} s"
        else "$seconds s"
}
