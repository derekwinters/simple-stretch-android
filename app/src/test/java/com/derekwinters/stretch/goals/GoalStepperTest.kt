package com.derekwinters.stretch.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Requirement IDs refer to docs/spec/goals.md. */
class GoalStepperTest {

    // GOAL-002: + on 0 adds a goal of 1.
    @Test
    fun plusOnZeroAddsGoalOfOne() {
        assertEquals(StepperEdit.Set(1), GoalStepper.plan(0, StepperTap.Plus))
    }

    // GOAL-002: + / − on 1 to 20 changes the count by one.
    @Test
    fun plusAndMinusChangeByOne() {
        assertEquals(StepperEdit.Set(4), GoalStepper.plan(3, StepperTap.Plus))
        assertEquals(StepperEdit.Set(2), GoalStepper.plan(3, StepperTap.Minus))
        assertEquals(StepperEdit.Set(20), GoalStepper.plan(19, StepperTap.Plus))
        assertEquals(StepperEdit.Set(19), GoalStepper.plan(20, StepperTap.Minus))
        assertEquals(StepperEdit.Set(2), GoalStepper.plan(1, StepperTap.Plus))
    }

    // GOAL-002 / GOAL-006: − on 1 removes the goal.
    @Test
    fun minusOnOneRemovesGoal() {
        assertEquals(StepperEdit.Remove, GoalStepper.plan(1, StepperTap.Minus))
    }

    // GOAL-001 / GOAL-002: − at 0 and + at 20 do nothing, and the buttons are disabled there.
    @Test
    fun boundsDoNothing() {
        assertEquals(StepperEdit.None, GoalStepper.plan(0, StepperTap.Minus))
        assertEquals(StepperEdit.None, GoalStepper.plan(20, StepperTap.Plus))
        assertFalse(GoalStepper.canDecrease(0))
        assertTrue(GoalStepper.canDecrease(1))
        assertFalse(GoalStepper.canIncrease(20))
        assertTrue(GoalStepper.canIncrease(19))
        assertEquals(20, GoalStepper.MAX)
    }

    // GOAL-002: every stretch is listed by name (ignoring case), 0 without a goal.
    @Test
    fun goalRowsListEveryStretchByName() {
        val rows = GoalMath.goalRows(
            stretches = listOf(StretchRef(1, "neck rolls"), StretchRef(2, "Calf"), StretchRef(3, "Hamstring")),
            goals = listOf(GoalRef(3, 3), GoalRef(99, 2)),
            countsToday = mapOf(3L to 3, 1L to 2),
        )
        assertEquals(listOf("Calf", "Hamstring", "neck rolls"), rows.map { it.stretchName })
        assertEquals(listOf(0, 3, 0), rows.map { it.count })
        assertEquals(listOf(0, 3, 2), rows.map { it.done })
        // GOAL-002: the ✓ is for goals only; a stretch at 0 is never "met".
        assertEquals(listOf(false, true, false), rows.map { it.met })
        assertEquals(listOf(false, true, false), rows.map { it.hasGoal })
    }

    /**
     * A minimal in-memory goals table for one stretch that applies [GoalEdit]s the way
     * `GoalDao.setGoal` / `removeGoal` do, so tap sequences can be checked end to end.
     */
    private class FakeGoalRows {
        data class Row(val id: Long, val count: Int, val from: Long, val to: Long?)
        val rows = mutableListOf<Row>()
        private var nextId = 1L
        val current: Row? get() = rows.singleOrNull { it.to == null }
        val count: Int get() = current?.count ?: 0

        fun tap(tap: StepperTap, today: LocalDate) {
            val cur = current?.let { CurrentGoal(it.id, it.count, it.from) }
            val edit = when (val e = GoalStepper.plan(count, tap)) {
                StepperEdit.None -> GoalEdit.None
                is StepperEdit.Set -> GoalHistory.planSet(cur, e.timesPerDay, today)
                StepperEdit.Remove -> GoalHistory.planRemove(cur, today)
            }
            apply(edit)
        }

        fun apply(edit: GoalEdit) {
            when (edit) {
                GoalEdit.None -> Unit
                is GoalEdit.Insert -> rows += Row(nextId++, edit.timesPerDay, edit.fromEpochDay, null)
                is GoalEdit.UpdateInPlace -> replace(edit.id) { it.copy(count = edit.timesPerDay) }
                is GoalEdit.CloseAndInsert -> {
                    val from = rows.first { it.id == edit.id }.from
                    replace(edit.id) { it.copy(to = edit.toEpochDay) }
                    rows += Row(nextId++, edit.timesPerDay, edit.toEpochDay, null)
                    check(from < edit.toEpochDay)
                }
                is GoalEdit.Delete -> rows.removeAll { it.id == edit.id }
                is GoalEdit.Close -> replace(edit.id) { it.copy(to = edit.toEpochDay) }
            }
        }

        private fun replace(id: Long, f: (Row) -> Row) {
            val i = rows.indexOfFirst { it.id == id }
            rows[i] = f(rows[i])
        }
    }

    private val today = LocalDate.of(2026, 10, 21)
    private val todayDay = today.toEpochDay()

    // GOAL-002 invariant / GOAL-009: repeated same-day taps from no goal leave one row for today.
    @Test
    fun repeatedTapsFromNoGoalLeaveOneRow() {
        val t = FakeGoalRows()
        repeat(5) { t.tap(StepperTap.Plus, today) }
        t.tap(StepperTap.Minus, today)
        assertEquals(listOf(FakeGoalRows.Row(1, 4, todayDay, null)), t.rows)
        // Back down to 0 on the same day: the row that started today is deleted, no history.
        repeat(4) { t.tap(StepperTap.Minus, today) }
        assertTrue(t.rows.isEmpty())
        t.tap(StepperTap.Minus, today)
        assertTrue(t.rows.isEmpty())
    }

    // GOAL-002 invariant / GOAL-009: taps on an older goal close it once, then edit today's row.
    @Test
    fun repeatedTapsOnOlderGoalCloseItOnce() {
        val t = FakeGoalRows()
        t.apply(GoalEdit.Insert(3, todayDay - 7))
        repeat(3) { t.tap(StepperTap.Plus, today) }
        t.tap(StepperTap.Minus, today)
        assertEquals(
            listOf(
                FakeGoalRows.Row(1, 3, todayDay - 7, todayDay),
                FakeGoalRows.Row(2, 5, todayDay, null),
            ),
            t.rows,
        )
        // + up to 20 then more taps: stays at 20, still two rows.
        repeat(30) { t.tap(StepperTap.Plus, today) }
        assertEquals(20, t.count)
        assertEquals(2, t.rows.size)
    }

    // GOAL-006 / GOAL-009: − on 1 on an older goal closes it, keeping the history row.
    @Test
    fun minusToZeroKeepsHistory() {
        val t = FakeGoalRows()
        t.apply(GoalEdit.Insert(1, todayDay - 3))
        t.tap(StepperTap.Minus, today)
        assertEquals(listOf(FakeGoalRows.Row(1, 1, todayDay - 3, todayDay)), t.rows)
        assertEquals(0, t.count)
    }
}
