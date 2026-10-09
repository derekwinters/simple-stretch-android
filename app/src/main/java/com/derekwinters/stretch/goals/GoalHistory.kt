package com.derekwinters.stretch.goals

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure (Android-free) rules for versioned goals (GOAL-008, GOAL-009). The repository turns a
 * [GoalEdit] into Room writes inside one transaction.
 */

/** The open (current) goal row for a stretch: its id, count and first day in force. */
data class CurrentGoal(val id: Long, val timesPerDay: Int, val fromEpochDay: Long)

/** What a goal change does to the history table. */
sealed interface GoalEdit {
    /** Nothing changes (same count). */
    data object None : GoalEdit

    /** Add a new open row starting [fromEpochDay]. */
    data class Insert(val timesPerDay: Int, val fromEpochDay: Long) : GoalEdit

    /** The current row started today: change it in place (no one-day-long history rows). */
    data class UpdateInPlace(val id: Long, val timesPerDay: Int) : GoalEdit

    /** Close row [id] as of [toEpochDay] (exclusive), then add a new open row from that day. */
    data class CloseAndInsert(val id: Long, val toEpochDay: Long, val timesPerDay: Int) : GoalEdit

    /** The current row started today: delete it, so today has no goal for this stretch. */
    data class Delete(val id: Long) : GoalEdit

    /** Close row [id] as of [toEpochDay] (exclusive). */
    data class Close(val id: Long, val toEpochDay: Long) : GoalEdit
}

object GoalHistory {
    /**
     * GOAL-009: setting a stretch's goal to [timesPerDay] (clamped to at least 1, GOAL-001) on
     * [today]. The new count applies from today; earlier days keep the count they had.
     */
    fun planSet(current: CurrentGoal?, timesPerDay: Int, today: LocalDate): GoalEdit {
        val count = timesPerDay.coerceAtLeast(1)
        val day = today.toEpochDay()
        return when {
            current == null -> GoalEdit.Insert(count, day)
            current.timesPerDay == count -> GoalEdit.None
            current.fromEpochDay >= day -> GoalEdit.UpdateInPlace(current.id, count)
            else -> GoalEdit.CloseAndInsert(current.id, day, count)
        }
    }

    /** GOAL-009: removing a stretch's goal on [today]; today is judged without it. */
    fun planRemove(current: CurrentGoal?, today: LocalDate): GoalEdit {
        val day = today.toEpochDay()
        return when {
            current == null -> GoalEdit.None
            current.fromEpochDay >= day -> GoalEdit.Delete(current.id)
            else -> GoalEdit.Close(current.id, day)
        }
    }

    /**
     * GOAL-010: the first day in force given to goals carried over from database version 2: the
     * local date of the earliest logged completion, or [today] when nothing was logged yet (or
     * the earliest completion is somehow in the future).
     */
    fun migratedStartDay(earliestCompletionMillis: Long?, today: LocalDate, zone: ZoneId): Long {
        val todayDay = today.toEpochDay()
        if (earliestCompletionMillis == null) return todayDay
        val first = Instant.ofEpochMilli(earliestCompletionMillis).atZone(zone).toLocalDate().toEpochDay()
        return minOf(first, todayDay)
    }
}
