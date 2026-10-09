package com.derekwinters.stretch.data

import android.content.Context
import androidx.room.withTransaction
import com.derekwinters.stretch.goals.GoalMath
import com.derekwinters.stretch.goals.GoalProgress
import com.derekwinters.stretch.goals.GoalRef
import com.derekwinters.stretch.goals.SessionEntry
import com.derekwinters.stretch.goals.StretchRef
import com.derekwinters.stretch.scheduling.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single entry point for reading and changing app data. Every write that can change when a
 * reminder fires is followed by a full reschedule (SCHED-006).
 */
class StretchRepository(
    context: Context,
    private val db: AppDatabase,
) {
    private val appContext = context.applicationContext

    val stretches: Flow<List<Stretch>> = db.stretchDao().observeAll()
    val schedules: Flow<List<ScheduleWithReminders>> = db.scheduleDao().observeAllWithReminders()
    val skippedDates: Flow<Set<LocalDate>> = db.skipDao().observeAll()
        .map { list -> list.map { LocalDate.ofEpochDay(it.epochDay) }.toSet() }

    val goals: Flow<List<Goal>> = db.goalDao().observeAll()

    /** Completions per stretch id on [date] (GOAL-003). */
    fun countsOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<Map<Long, Int>> {
        val (from, to) = GoalMath.dayBounds(date, zone)
        return db.completionDao().observeCountsBetween(from, to)
            .map { rows -> rows.associate { it.stretchId to it.count } }
    }

    private suspend fun countsOnce(date: LocalDate, zone: ZoneId): Map<Long, Int> {
        val (from, to) = GoalMath.dayBounds(date, zone)
        return db.completionDao().countsBetween(from, to).associate { it.stretchId to it.count }
    }

    /** One-shot goal progress for [date], used by notifications (NOTIF-001). */
    suspend fun goalProgressOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<GoalProgress> =
        GoalMath.progress(
            db.stretchDao().getAll().map { StretchRef(it.id, it.name) },
            db.goalDao().getAll().map { GoalRef(it.stretchId, it.timesPerDay) },
            countsOnce(date, zone),
        )

    /** One-shot session list for [date], ordered once (SESS-002, SESS-005). */
    suspend fun sessionEntries(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<SessionEntry> =
        GoalMath.sessionOrder(
            db.stretchDao().getAll().map { StretchRef(it.id, it.name) },
            db.goalDao().getAll().map { GoalRef(it.stretchId, it.timesPerDay) },
            countsOnce(date, zone),
        )

    /** GOAL-001: one goal per stretch; the count is clamped to at least 1 here, not only in the UI. */
    suspend fun setGoal(stretchId: Long, timesPerDay: Int) {
        db.goalDao().upsert(Goal(stretchId = stretchId, timesPerDay = timesPerDay.coerceAtLeast(1)))
    }

    /** GOAL-006: removing a goal keeps the stretch's completion history. */
    suspend fun removeGoal(stretchId: Long) {
        db.goalDao().deleteForStretch(stretchId)
    }

    /** SESS-003: one completion per stretch, one timestamp, one transaction; append only. */
    suspend fun logCompletions(stretchIds: Collection<Long>, atMillis: Long = System.currentTimeMillis()) {
        if (stretchIds.isEmpty()) return
        db.withTransaction {
            db.completionDao().insertAll(stretchIds.distinct().map { Completion(stretchId = it, completedAt = atMillis) })
        }
    }

    suspend fun getSchedule(id: Long): ScheduleWithReminders? = db.scheduleDao().getWithReminders(id)

    suspend fun saveStretch(stretch: Stretch) {
        if (stretch.id == 0L) db.stretchDao().insert(stretch) else db.stretchDao().update(stretch)
    }

    suspend fun deleteStretch(stretch: Stretch) {
        db.stretchDao().delete(stretch)
    }

    suspend fun saveSchedule(schedule: Schedule, reminders: List<ReminderDraft>): Long {
        val id = db.scheduleDao().saveSchedule(schedule, reminders)
        reschedule()
        return id
    }

    suspend fun deleteSchedule(id: Long) {
        db.scheduleDao().deleteSchedule(id)
        reschedule()
    }

    suspend fun setScheduleEnabled(id: Long, enabled: Boolean) {
        db.scheduleDao().setEnabled(id, enabled)
        reschedule()
    }

    suspend fun skipDate(date: LocalDate) {
        db.skipDao().insert(SkippedDate(date.toEpochDay()))
        reschedule()
    }

    suspend fun unskipDate(date: LocalDate) {
        db.skipDao().delete(date.toEpochDay())
        reschedule()
    }

    private suspend fun reschedule() = ReminderScheduler.rescheduleAll(appContext)
}
