package com.derekwinters.stretch.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.derekwinters.stretch.scheduling.DaysOfWeek
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
)

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
