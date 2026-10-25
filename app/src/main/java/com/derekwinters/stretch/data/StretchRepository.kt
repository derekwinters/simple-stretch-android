package com.derekwinters.stretch.data

import android.content.Context
import androidx.room.withTransaction
import com.derekwinters.stretch.goals.GoalMath
import com.derekwinters.stretch.goals.GoalProgress
import com.derekwinters.stretch.goals.GoalRef
import com.derekwinters.stretch.goals.SessionLayout
import com.derekwinters.stretch.goals.StretchRef
import com.derekwinters.stretch.trends.CompletionRef
import com.derekwinters.stretch.trends.GoalPeriod
import com.derekwinters.stretch.scheduling.ReminderScheduler
import com.derekwinters.stretch.widget.GoalRing
import com.derekwinters.stretch.widget.RingModel
import com.derekwinters.stretch.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single entry point for reading and changing app data. Every write that can change when a
 * reminder fires is followed by a full reschedule (SCHED-006), and every write that can change
 * today's goals or progress by a widget redraw (WIDGET-010).
 *
 * The current date, time and zone come from [clock], the app clock (SCHED-016): a zone left out
 * by a caller is the clock's zone at the time of the call (SCHED-010 invariant).
 */
class StretchRepository(
    context: Context,
    private val db: AppDatabase,
    private val clock: Clock,
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
    fun completionsOn(date: LocalDate, zone: ZoneId = clock.zone): Flow<List<CompletionRef>> {
        val (from, to) = GoalMath.dayBounds(date, zone)
        return db.completionDao().observeBetween(from, to).map { rows -> rows.map { CompletionRef(it.stretchId, it.completedAt) } }
    }

    /** Every completion from the start of [date] on, oldest first (TREND-005, TREND-006). */
    fun completionsSince(date: LocalDate, zone: ZoneId = clock.zone): Flow<List<CompletionRef>> {
        val (from, _) = GoalMath.dayBounds(date, zone)
        return db.completionDao().observeSince(from).map { rows -> rows.map { CompletionRef(it.stretchId, it.completedAt) } }
    }

    /** Completions per stretch id on [date] (GOAL-003). */
    fun countsOn(date: LocalDate, zone: ZoneId = clock.zone): Flow<Map<Long, Int>> {
        val (from, to) = GoalMath.dayBounds(date, zone)
        return db.completionDao().observeCountsBetween(from, to)
            .map { rows -> rows.associate { it.stretchId to it.count } }
    }

    private suspend fun countsOnce(date: LocalDate, zone: ZoneId): Map<Long, Int> {
        val (from, to) = GoalMath.dayBounds(date, zone)
        return db.completionDao().countsBetween(from, to).associate { it.stretchId to it.count }
    }

    /** One-shot goal progress for [date], used by notifications (NOTIF-001). */
    suspend fun goalProgressOn(date: LocalDate, zone: ZoneId = clock.zone): List<GoalProgress> =
        GoalMath.progress(
            db.stretchDao().getAll().map { StretchRef(it.id, it.name) },
            db.goalDao().getCurrent().map { GoalRef(it.stretchId, it.timesPerDay) },
            countsOnce(date, zone),
        )

    /**
     * One-shot session sections for [date], decided once (SESS-002, SESS-005).
     * [reminderStretchIds] are the stretches the opening reminder named, or null.
     */
    suspend fun sessionLayout(
        date: LocalDate,
        reminderStretchIds: List<Long>?,
        zone: ZoneId = clock.zone,
    ): SessionLayout =
        GoalMath.sessionLayout(
            db.stretchDao().getAll().map { StretchRef(it.id, it.name) },
            db.goalDao().getCurrent().map { GoalRef(it.stretchId, it.timesPerDay) },
            countsOnce(date, zone),
            reminderStretchIds,
        )

    /** One-shot segmented ring for [date], from the goals screen's calculation (WIDGET-009). */
    suspend fun goalRingOn(date: LocalDate, zone: ZoneId = clock.zone): RingModel =
        GoalRing.fromLog(
            db.stretchDao().getAll().map { StretchRef(it.id, it.name) },
            db.goalDao().getCurrent().map { GoalRef(it.stretchId, it.timesPerDay) },
            countsOnce(date, zone),
        )

    /**
     * GOAL-001/009: one current goal per stretch; the count is clamped to at least 1 (in
     * `GoalHistory.planSet`, not only in the UI). The new count applies from today; earlier days
     * keep theirs.
     */
    suspend fun setGoal(stretchId: Long, timesPerDay: Int) {
        db.goalDao().setGoal(stretchId, timesPerDay, LocalDate.now(clock))
        redrawWidgets()
    }

    /** GOAL-006/009: removing a goal keeps the completion history and the goal's past versions. */
    suspend fun removeGoal(stretchId: Long) {
        db.goalDao().removeGoal(stretchId, LocalDate.now(clock))
        redrawWidgets()
    }

    /**
     * SESS-003: one completion per stretch, one timestamp (now, from the app clock), one
     * transaction; append only.
     */
    suspend fun logCompletions(stretchIds: Collection<Long>) {
        if (stretchIds.isEmpty()) return
        val atMillis = clock.millis()
        db.withTransaction {
            db.completionDao().insertAll(stretchIds.distinct().map { Completion(stretchId = it, completedAt = atMillis) })
        }
        redrawWidgets()
    }

    suspend fun getSchedule(id: Long): ScheduleWithReminders? = db.scheduleDao().getWithReminders(id)

    /** Inserts (id 0) or updates [stretch]; returns its id, so a picker can select a new one (LIB-007). */
    suspend fun saveStretch(stretch: Stretch): Long {
        // WIDGET-010: a rename can change the arcs' order.
        val id = if (stretch.id != 0L) {
            db.stretchDao().update(stretch)
            stretch.id
        } else {
            db.stretchDao().insert(stretch)
        }
        redrawWidgets()
        return id
    }

    suspend fun deleteStretch(stretch: Stretch) {
        db.stretchDao().delete(stretch)
        redrawWidgets()
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

    /** WIDGET-010 invariant: made here, after the write, so no screen can forget it. */
    private fun redrawWidgets() = WidgetUpdater.request(appContext)
}
