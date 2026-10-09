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

    /** NOTIF-001..004: stretch names + descriptions, tap opens app, Done and Skip-rest-of-today. */
    fun showReminder(context: Context, reminderId: Long, scheduleName: String, stretches: List<Stretch>) {
        if (!canPost(context)) return
        val notificationId = reminderId.toInt()

        val title = if (stretches.isEmpty()) {
            context.getString(R.string.notification_generic_title)
        } else {
            stretches.joinToString(", ") { it.name }
        }
        val body = if (stretches.isEmpty()) {
            context.getString(R.string.notification_generic_body)
        } else {
            stretches.joinToString("\n\n") { s ->
                buildString {
                    append(s.name)
                    s.durationSeconds?.let { append(" (").append(formatDuration(it)).append(")") }
                    if (s.description.isNotBlank()) append(": ").append(s.description)
                }
            }
        }

        val openApp = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (stretches.size == 1) stretches.first().description.ifBlank { scheduleName } else scheduleName)
            .setSubText(scheduleName)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(
                0,
                context.getString(R.string.action_done),
                actionIntent(context, NotificationActionReceiver.ACTION_DONE, notificationId),
            )
            .addAction(
                0,
                context.getString(R.string.action_skip_today),
                actionIntent(context, NotificationActionReceiver.ACTION_SKIP_TODAY, notificationId),
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing else to do.
        }
    }

    private fun actionIntent(context: Context, action: String, notificationId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(action)
                .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun formatDuration(seconds: Int): String =
        if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60} min"
        else if (seconds >= 60) "${seconds / 60} min ${seconds % 60} s"
        else "$seconds s"
}
