package com.derekwinters.stretch.data

import android.content.Context
import com.derekwinters.stretch.scheduling.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

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
