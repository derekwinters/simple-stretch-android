package com.derekwinters.stretch.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StretchDao {
    @Query("SELECT * FROM stretches ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Stretch>>

    @Insert
    suspend fun insert(stretch: Stretch): Long

    @Update
    suspend fun update(stretch: Stretch)

    @Delete
    suspend fun delete(stretch: Stretch)
}

@Dao
abstract class ScheduleDao {
    @Transaction
    @Query("SELECT * FROM schedules ORDER BY name COLLATE NOCASE")
    abstract fun observeAllWithReminders(): Flow<List<ScheduleWithReminders>>

    @Transaction
    @Query("SELECT * FROM schedules")
    abstract suspend fun getAllWithReminders(): List<ScheduleWithReminders>

    @Transaction
    @Query("SELECT * FROM schedules WHERE id = :id")
    abstract suspend fun getWithReminders(id: Long): ScheduleWithReminders?

    @Transaction
    @Query("SELECT * FROM reminders WHERE id = :id")
    abstract suspend fun getReminder(id: Long): ReminderWithStretches?

    @Query("SELECT * FROM schedules WHERE id = :id")
    abstract suspend fun getSchedule(id: Long): Schedule?

    @Insert
    abstract suspend fun insertSchedule(schedule: Schedule): Long

    @Update
    abstract suspend fun updateSchedule(schedule: Schedule)

    @Query("DELETE FROM reminders WHERE scheduleId = :scheduleId")
    abstract suspend fun deleteRemindersFor(scheduleId: Long)

    @Insert
    abstract suspend fun insertReminder(reminder: Reminder): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCrossRefs(refs: List<ReminderStretchCrossRef>)

    @Query("UPDATE schedules SET enabled = :enabled WHERE id = :id")
    abstract suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM schedules WHERE id = :id")
    abstract suspend fun deleteSchedule(id: Long)

    /** Inserts or updates [schedule] and replaces all of its reminders. Returns the schedule id. */
    @Transaction
    open suspend fun saveSchedule(schedule: Schedule, reminders: List<ReminderDraft>): Long {
        val id = if (schedule.id == 0L) {
            insertSchedule(schedule)
        } else {
            updateSchedule(schedule)
            schedule.id
        }
        deleteRemindersFor(id)
        for (draft in reminders) {
            val reminderId = insertReminder(Reminder(scheduleId = id, minuteOfDay = draft.minuteOfDay))
            if (draft.stretchIds.isNotEmpty()) {
                insertCrossRefs(draft.stretchIds.distinct().map { ReminderStretchCrossRef(reminderId, it) })
            }
        }
        return id
    }
}

@Dao
interface SkipDao {
    @Query("SELECT * FROM skipped_dates ORDER BY epochDay")
    fun observeAll(): Flow<List<SkippedDate>>

    @Query("SELECT * FROM skipped_dates")
    suspend fun getAll(): List<SkippedDate>

    @Query("SELECT EXISTS(SELECT 1 FROM skipped_dates WHERE epochDay = :epochDay)")
    suspend fun isSkipped(epochDay: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(date: SkippedDate)

    @Query("DELETE FROM skipped_dates WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    @Query("DELETE FROM skipped_dates WHERE epochDay < :epochDay")
    suspend fun deleteBefore(epochDay: Long)
}
