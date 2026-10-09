package com.derekwinters.stretch.goals

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Requirement IDs refer to docs/spec/goals.md. */
class GoalHistoryTest {
    private val today = LocalDate.of(2026, 10, 9)
    private val todayDay = today.toEpochDay()
    private val older = CurrentGoal(id = 7, timesPerDay = 3, fromEpochDay = todayDay - 10)
    private val fromToday = CurrentGoal(id = 8, timesPerDay = 3, fromEpochDay = todayDay)

    // GOAL-009: a new goal starts today.
    @Test
    fun newGoalStartsToday() {
        assertEquals(GoalEdit.Insert(2, todayDay), GoalHistory.planSet(null, 2, today))
    }

    // GOAL-009: changing an older goal closes it as of today and starts a new row today.
    @Test
    fun changeClosesAndInserts() {
        assertEquals(GoalEdit.CloseAndInsert(7, todayDay, 5), GoalHistory.planSet(older, 5, today))
    }

    // GOAL-009: a goal that started today is changed in place; the same count changes nothing.
    @Test
    fun sameDayAndUnchangedEdits() {
        assertEquals(GoalEdit.UpdateInPlace(8, 1), GoalHistory.planSet(fromToday, 1, today))
        assertEquals(GoalEdit.None, GoalHistory.planSet(older, 3, today))
    }

    // GOAL-001: the count is clamped to at least 1.
    @Test
    fun countIsClamped() {
        assertEquals(GoalEdit.Insert(1, todayDay), GoalHistory.planSet(null, 0, today))
        assertEquals(GoalEdit.CloseAndInsert(7, todayDay, 1), GoalHistory.planSet(older, -4, today))
    }

    // GOAL-009: removing closes an older goal (history kept) or deletes one that started today.
    @Test
    fun remove() {
        assertEquals(GoalEdit.Close(7, todayDay), GoalHistory.planRemove(older, today))
        assertEquals(GoalEdit.Delete(8), GoalHistory.planRemove(fromToday, today))
        assertEquals(GoalEdit.None, GoalHistory.planRemove(null, today))
    }

    // GOAL-010: migrated goals start on the local day of the earliest completion, else today.
    @Test
    fun migratedStartDay() {
        val zone = ZoneId.of("America/Chicago")
        assertEquals(todayDay, GoalHistory.migratedStartDay(null, today, zone))
        val lateEvening = LocalDateTime.of(2026, 10, 2, 23, 30).atZone(zone).toInstant().toEpochMilli()
        // 04:30 UTC on the 3rd, but the 2nd locally.
        assertEquals(LocalDate.of(2026, 10, 2).toEpochDay(), GoalHistory.migratedStartDay(lateEvening, today, zone))
        val future = LocalDateTime.of(2026, 12, 1, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(todayDay, GoalHistory.migratedStartDay(future, today, zone))
    }
}
