package com.derekwinters.stretch.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.derekwinters.stretch.goals.CurrentGoal
import com.derekwinters.stretch.goals.GoalEdit
import com.derekwinters.stretch.goals.GoalHistory
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface StretchDao {
    @Query("SELECT * FROM stretches ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Stretch>>

    @Query("SELECT * FROM stretches ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Stretch>

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
}

@Dao
abstract class GoalDao {
    /** Current goals only (GOAL-008): what the rest of the app means by "the goals". */
    @Query("SELECT * FROM goals WHERE effectiveToEpochDay IS NULL")
    abstract fun observeCurrent(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE effectiveToEpochDay IS NULL")
    abstract suspend fun getCurrent(): List<Goal>

    /** Every version, current and closed, for trends (TREND-001). */
    @Query("SELECT * FROM goals ORDER BY effectiveFromEpochDay, id")
    abstract fun observeHistory(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE stretchId = :stretchId AND effectiveToEpochDay IS NULL ORDER BY effectiveFromEpochDay DESC, id DESC LIMIT 1")
    abstract suspend fun getCurrentFor(stretchId: Long): Goal?

    @Insert
    abstract suspend fun insert(goal: Goal): Long

    @Query("UPDATE goals SET timesPerDay = :timesPerDay WHERE id = :id")
    abstract suspend fun updateCount(id: Long, timesPerDay: Int)

    @Query("UPDATE goals SET effectiveToEpochDay = :toEpochDay WHERE id = :id")
    abstract suspend fun close(id: Long, toEpochDay: Long)

    @Query("DELETE FROM goals WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** GOAL-009: one transaction, so a stretch never ends up with two current rows or none. */
    @Transaction
    open suspend fun setGoal(stretchId: Long, timesPerDay: Int, today: LocalDate) {
        applyEdit(stretchId, GoalHistory.planSet(currentFor(stretchId), timesPerDay, today))
    }

    /** GOAL-006/009: closes (or, if it started today, deletes) the current row; history stays. */
    @Transaction
    open suspend fun removeGoal(stretchId: Long, today: LocalDate) {
        applyEdit(stretchId, GoalHistory.planRemove(currentFor(stretchId), today))
    }

    private suspend fun currentFor(stretchId: Long): CurrentGoal? =
        getCurrentFor(stretchId)?.let { CurrentGoal(it.id, it.timesPerDay, it.effectiveFromEpochDay) }

    private suspend fun applyEdit(stretchId: Long, edit: GoalEdit) {
        when (edit) {
            GoalEdit.None -> Unit
            is GoalEdit.Insert ->
                insert(Goal(stretchId = stretchId, timesPerDay = edit.timesPerDay, effectiveFromEpochDay = edit.fromEpochDay))
            is GoalEdit.UpdateInPlace -> updateCount(edit.id, edit.timesPerDay)
            is GoalEdit.CloseAndInsert -> {
                close(edit.id, edit.toEpochDay)
                insert(Goal(stretchId = stretchId, timesPerDay = edit.timesPerDay, effectiveFromEpochDay = edit.toEpochDay))
            }
            is GoalEdit.Delete -> delete(edit.id)
            is GoalEdit.Close -> close(edit.id, edit.toEpochDay)
        }
    }
}

@Dao
interface CompletionDao {
    @Insert
    suspend fun insertAll(completions: List<Completion>)

    @Query(
        "SELECT stretchId, COUNT(*) AS count FROM completions " +
            "WHERE completedAt >= :fromMillis AND completedAt < :toMillis GROUP BY stretchId",
    )
    fun observeCountsBetween(fromMillis: Long, toMillis: Long): Flow<List<StretchCount>>

    @Query(
        "SELECT stretchId, COUNT(*) AS count FROM completions " +
            "WHERE completedAt >= :fromMillis AND completedAt < :toMillis GROUP BY stretchId",
    )
    suspend fun countsBetween(fromMillis: Long, toMillis: Long): List<StretchCount>

    /** Completions in [fromMillis, toMillis), oldest first (TREND-005..008). */
    @Query("SELECT * FROM completions WHERE completedAt >= :fromMillis AND completedAt < :toMillis ORDER BY completedAt, id")
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<Completion>>

    /** Completions at or after [fromMillis], oldest first (TREND-006). */
    @Query("SELECT * FROM completions WHERE completedAt >= :fromMillis ORDER BY completedAt, id")
    fun observeSince(fromMillis: Long): Flow<List<Completion>>
}
