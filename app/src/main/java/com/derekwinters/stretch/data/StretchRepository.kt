package com.derekwinters.stretch.data

import android.content.Context
import androidx.room.withTransaction
import com.derekwinters.stretch.goals.GoalMath
import com.derekwinters.stretch.goals.GoalProgress
import com.derekwinters.stretch.goals.GoalRef
import com.derekwinters.stretch.goals.SessionEntry
import com.derekwinters.stretch.goals.StretchRef
import com.derekwinters.stretch.trends.CompletionRef
import com.derekwinters.stretch.trends.GoalPeriod
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

    /** Current goals (GOAL-008): one per stretch at most. */
    val goals: Flow<List<Goal>> = db.goalDao().observeCurrent()

    /** Every goal version, current and closed, for trends (TREND-001). */
    val goalHistory: Flow<List<GoalPeriod>> = db.goalDao().observeHistory()
        .map { rows -> rows.map { GoalPeriod(it.stretchId, it.timesPerDay, it.effectiveFromEpochDay, it.effectiveToEpochDay) } }

    /** Every completion on [date], oldest first (TREND-007, TREND-008). */
    fun completionsOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<CompletionRef>> {
        val (from, to) = GoalMath.dayBounds(date, zone)
        return db.completionDao().observeBetween(from, to).map { rows -> rows.map { CompletionRef(it.stretchId, it.completedAt) } }
    }

    /** Every completion from the start of [date] on, oldest first (TREND-005, TREND-006). */
    fun completionsSince(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<CompletionRef>> {
        val (from, _) = GoalMath.dayBounds(date, zone)
        return db.completionDao().observeSince(from).map { rows -> rows.map { CompletionRef(it.stretchId, it.completedAt) } }
    }

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
            db.goalDao().getCurrent().map { GoalRef(it.stretchId, it.timesPerDay) },
            countsOnce(date, zone),
        )

    /** One-shot session list for [date], ordered once (SESS-002, SESS-005). */
    suspend fun sessionEntries(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<SessionEntry> =
        GoalMath.sessionOrder(
            db.stretchDao().getAll().map { StretchRef(it.id, it.name) },
            db.goalDao().getCurrent().map { GoalRef(it.stretchId, it.timesPerDay) },
            countsOnce(date, zone),
        )

    /**
     * GOAL-001/009: one current goal per stretch; the count is clamped to at least 1 (in
     * `GoalHistory.planSet`, not only in the UI). The new count applies from today; earlier days
     * keep theirs.
     */
    suspend fun setGoal(stretchId: Long, timesPerDay: Int, today: LocalDate = LocalDate.now()) {
        db.goalDao().setGoal(stretchId, timesPerDay, today)
    }

    /** GOAL-006/009: removing a goal keeps the completion history and the goal's past versions. */
    suspend fun removeGoal(stretchId: Long, today: LocalDate = LocalDate.now()) {
        db.goalDao().removeGoal(stretchId, today)
    }

    /** SESS-003: one completion per stretch, one timestamp, one transaction; append only. */
    suspend fun logCompletions(stretchIds: Collection<Long>, atMillis: Long = System.currentTimeMillis()) {
        if (stretchIds.isEmpty()) return
        db.withTransaction {
            db.completionDao().insertAll(stretchIds.distinct().map { Completion(stretchId = it, completedAt = atMillis) })
        }
    }

    suspend fun getSchedule(id: Long): ScheduleWithReminders? = db.scheduleDao().getWithReminders(id)

    /** Inserts (id 0) or updates [stretch]; returns its id, so a picker can select a new one (LIB-007). */
    suspend fun saveStretch(stretch: Stretch): Long {
        if (stretch.id != 0L) {
            db.stretchDao().update(stretch)
            return stretch.id
        }
        return db.stretchDao().insert(stretch)
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
