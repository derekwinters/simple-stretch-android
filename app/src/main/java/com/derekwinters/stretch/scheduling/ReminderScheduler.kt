package com.derekwinters.stretch.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.data.days
import com.derekwinters.stretch.data.time
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps exactly one alarm per active reminder, set for that reminder's next occurrence.
 *
 * The request code of each alarm's PendingIntent is the reminder id (SCHED-005), so setting it
 * again replaces the previous alarm. The trigger time of every alarm we set is remembered in
 * SharedPreferences, which lets us cancel alarms of reminders that no longer exist and keep a
 * just-due alarm that has not been delivered yet (see [GRACE_MILLIS]).
 */
object ReminderScheduler {
    private const val TAG = "ReminderScheduler"
    const val ACTION_FIRE = "com.derekwinters.stretch.action.FIRE_REMINDER"
    const val EXTRA_REMINDER_ID = "reminderId"
    const val EXTRA_TRIGGER_AT = "triggerAt"

    private const val PREFS = "reminder_scheduler"
    private const val PENDING_PREFIX = "pending_"

    /**
     * When an alarm's trigger time has passed but its receiver has not run yet (common with
     * inexact alarms), a reschedule must not jump that reminder to its next day. Alarms that came
     * due within this window are re-armed at their original time instead (fires immediately).
     */
    private const val GRACE_MILLIS = 30 * 60 * 1000L

    private val mutex = Mutex()

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    suspend fun rescheduleAll(context: Context) = mutex.withLock {
        val app = context.applicationContext as StretchApp
        val db = app.database
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val zone = ZoneId.systemDefault()
        val nowMillis = System.currentTimeMillis()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val today = now.toLocalDate()

        // Old skip entries are useless; keep yesterday so a late alarm can still be checked.
        db.skipDao().deleteBefore(today.minusDays(1).toEpochDay())
        val skipped: Set<LocalDate> = db.skipDao().getAll()
            .map { LocalDate.ofEpochDay(it.epochDay) }
            .toSet()

        val previous: Map<Long, Long> = prefs.all
            .filterKeys { it.startsWith(PENDING_PREFIX) }
            .mapNotNull { (key, value) ->
                val id = key.removePrefix(PENDING_PREFIX).toLongOrNull()
                val at = value as? Long
                if (id != null && at != null) id to at else null
            }
            .toMap()

        val scheduled = mutableMapOf<Long, Long>()
        for (item in db.scheduleDao().getAllWithReminders()) {
            if (!item.schedule.enabled) continue
            val days = item.schedule.days
            for (r in item.reminders) {
                val id = r.reminder.id
                val pendingAt = previous[id]
                // If this reminder's alarm came due moments ago and hasn't been handled yet,
                // compute from just before it so the same occurrence is kept.
                val base = if (pendingAt != null && pendingAt <= nowMillis && nowMillis - pendingAt < GRACE_MILLIS) {
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(pendingAt - 1), zone)
                } else {
                    now
                }
                val next = NextOccurrence.compute(base, r.reminder.time, days, skipped) ?: continue
                val triggerAt = next.atZone(zone).toInstant().toEpochMilli()
                setAlarm(app, id, triggerAt)
                scheduled[id] = triggerAt
            }
        }

        for (id in previous.keys - scheduled.keys) cancelAlarm(app, id)

        prefs.edit().apply {
            previous.keys.forEach { remove(PENDING_PREFIX + it) }
            scheduled.forEach { (id, at) -> putLong(PENDING_PREFIX + id, at) }
        }.apply()
    }

    /** Called by the alarm receiver once an occurrence has been handled. */
    suspend fun onAlarmHandled(context: Context, reminderId: Long) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(PENDING_PREFIX + reminderId)
            .commit()
        rescheduleAll(context)
    }

    private fun pendingIntent(context: Context, reminderId: Long, triggerAt: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_REMINDER_ID, reminderId)
            .putExtra(EXTRA_TRIGGER_AT, triggerAt)
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setAlarm(context: Context, reminderId: Long, triggerAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // Cancel first: FLAG_UPDATE_CURRENT on an immutable PendingIntent does not reliably
        // refresh extras for an alarm that is already registered.
        val stale = pendingIntent(context, reminderId, triggerAt)
        am.cancel(stale)
        stale.cancel()
        val pi = pendingIntent(context, reminderId, triggerAt)
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm refused; falling back to inexact", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun cancelAlarm(context: Context, reminderId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, reminderId, 0L)
        am.cancel(pi)
        pi.cancel()
    }
}
