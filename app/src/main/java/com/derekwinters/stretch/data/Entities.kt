package com.derekwinters.stretch.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.derekwinters.stretch.scheduling.DaysOfWeek
import com.derekwinters.stretch.scheduling.RepeatRule
import java.time.DayOfWeek
import java.time.LocalTime

@Entity(tableName = "stretches")
data class Stretch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val durationSeconds: Int? = null,
)

@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    /** Bit 0 = Monday ... bit 6 = Sunday; see [DaysOfWeek]. */
    val daysMask: Int,
    /** [ScheduleMode.FIXED] (set reminder times) or [ScheduleMode.REPEATING] (SCHED-001). */
    // The defaultValue literals must match MIGRATION_1_2's ALTER TABLE statements (GOAL-007).
    @ColumnInfo(defaultValue = "0") val mode: Int = ScheduleMode.FIXED,
    /** Repeating only (SCHED-011): window start/end as minutes after midnight, interval, minute. */
    @ColumnInfo(defaultValue = "480") val windowStartMinute: Int = RepeatRule.DEFAULT_START,
    @ColumnInfo(defaultValue = "1020") val windowEndMinute: Int = RepeatRule.DEFAULT_END,
    @ColumnInfo(defaultValue = "60") val intervalMinutes: Int = RepeatRule.DEFAULT_INTERVAL,
    @ColumnInfo(defaultValue = "0") val minutePastHour: Int = RepeatRule.DEFAULT_MINUTE,
)

object ScheduleMode {
    const val FIXED = 0
    const val REPEATING = 1
}

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = Schedule::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scheduleId")],
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    /** Minutes after local midnight (0..1439). */
    val minuteOfDay: Int,
)

@Entity(
    tableName = "reminder_stretches",
    primaryKeys = ["reminderId", "stretchId"],
    foreignKeys = [
        ForeignKey(
            entity = Reminder::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Stretch::class,
            parentColumns = ["id"],
            childColumns = ["stretchId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("stretchId")],
)
data class ReminderStretchCrossRef(
    val reminderId: Long,
    val stretchId: Long,
)

/** A whole local date on which no reminders fire. Stored as [java.time.LocalDate.toEpochDay]. */
@Entity(tableName = "skipped_dates")
data class SkippedDate(
    @PrimaryKey val epochDay: Long,
)

/** A daily goal for one stretch (GOAL-001). At most one per stretch. */
@Entity(
    tableName = "goals",
    foreignKeys = [
        ForeignKey(
            entity = Stretch::class,
            parentColumns = ["id"],
            childColumns = ["stretchId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["stretchId"], unique = true)],
)
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stretchId: Long,
    val timesPerDay: Int,
)

/** One logged completion of a stretch (SESS-004). */
@Entity(
    tableName = "completions",
    foreignKeys = [
        ForeignKey(
            entity = Stretch::class,
            parentColumns = ["id"],
            childColumns = ["stretchId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("stretchId"), Index("completedAt")],
)
data class Completion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stretchId: Long,
    /** Epoch milliseconds. */
    val completedAt: Long,
)

/** Result row: completions per stretch in a time range. */
data class StretchCount(
    val stretchId: Long,
    val count: Int,
)

data class ReminderWithStretches(
    @Embedded val reminder: Reminder,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ReminderStretchCrossRef::class,
            parentColumn = "reminderId",
            entityColumn = "stretchId",
        ),
    )
    val stretches: List<Stretch>,
)

data class ScheduleWithReminders(
    @Embedded val schedule: Schedule,
    @Relation(
        entity = Reminder::class,
        parentColumn = "id",
        entityColumn = "scheduleId",
    )
    val reminders: List<ReminderWithStretches>,
)

/** Input used when saving a schedule: one reminder time and the stretches it should prompt. */
data class ReminderDraft(
    val minuteOfDay: Int,
    val stretchIds: List<Long>,
)

// Derived values are extensions (not entity properties) so Room never tries to map them.
val Schedule.days: Set<DayOfWeek> get() = DaysOfWeek.fromMask(daysMask)

val Reminder.time: LocalTime get() = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

val Schedule.isRepeating: Boolean get() = mode == ScheduleMode.REPEATING

val Schedule.repeatRule: RepeatRule
    get() = RepeatRule(windowStartMinute, windowEndMinute, intervalMinutes, minutePastHour)
