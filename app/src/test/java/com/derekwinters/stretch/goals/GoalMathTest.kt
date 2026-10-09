package com.derekwinters.stretch.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Requirement IDs refer to docs/spec/. */
class GoalMathTest {
    private val hamstring = StretchRef(1, "Hamstring stretch")
    private val calf = StretchRef(2, "Calf stretch")
    private val neck = StretchRef(3, "neck rolls")
    private val wrist = StretchRef(4, "Wrist stretch")
    private val stretches = listOf(hamstring, calf, neck, wrist)
    private val goals = listOf(GoalRef(1, 3), GoalRef(2, 3), GoalRef(4, 1))

    // GOAL-003: progress counts and "met" at or above the target.
    @Test
    fun progressPerGoal() {
        val p = GoalMath.progress(stretches, goals, mapOf(1L to 2, 2L to 3, 3L to 5))
        assertEquals(listOf("Calf stretch", "Hamstring stretch", "Wrist stretch"), p.map { it.stretchName })
        val ham = p.first { it.stretchId == 1L }
        assertEquals(2, ham.done)
        assertEquals(1, ham.remaining)
        assertFalse(ham.met)
        assertTrue(p.first { it.stretchId == 2L }.met)
        assertEquals(0, p.first { it.stretchId == 4L }.done)
    }

    // GOAL-003: over-target is shown as is; GOAL-001: count is at least 1.
    @Test
    fun overTargetAndClampedTarget() {
        val p = GoalMath.progress(stretches, listOf(GoalRef(1, 3), GoalRef(2, 0)), mapOf(1L to 4))
        val ham = p.first { it.stretchId == 1L }
        assertEquals(4, ham.done)
        assertTrue(ham.met)
        assertEquals(1f, ham.fraction)
        assertEquals(1, p.first { it.stretchId == 2L }.target)
    }

    // GOAL-003: a goal whose stretch no longer exists is dropped.
    @Test
    fun goalForMissingStretchIsIgnored() {
        assertTrue(GoalMath.progress(listOf(calf), listOf(GoalRef(99, 2)), emptyMap()).isEmpty())
    }

    // SESS-002: unmet goals first (by name), then everything else by name, ignoring case.
    @Test
    fun sessionOrderPutsUnmetGoalsFirst() {
        val order = GoalMath.sessionOrder(stretches, goals, mapOf(2L to 3))
        assertEquals(
            listOf("Hamstring stretch", "Wrist stretch", "Calf stretch", "neck rolls"),
            order.map { it.name },
        )
        assertEquals(3, order.first { it.stretchId == 2L }.target)
        assertNull(order.first { it.stretchId == 3L }.target)
    }

    // SESS-002: with no goals the list is simply alphabetical.
    @Test
    fun sessionOrderWithoutGoals() {
        val order = GoalMath.sessionOrder(stretches, emptyList(), emptyMap())
        assertEquals(listOf("Calf stretch", "Hamstring stretch", "neck rolls", "Wrist stretch"), order.map { it.name })
        assertTrue(order.none { it.goalUnmet })
    }

    // GOAL-003: a day is local midnight to next midnight, including a 23-hour DST day.
    @Test
    fun dayBoundsFollowLocalMidnight() {
        val zone = ZoneId.of("America/Chicago")
        val (start, end) = GoalMath.dayBounds(LocalDate.of(2026, 10, 5), zone)
        assertEquals(24 * 60 * 60 * 1000L, end - start)
        val (s2, e2) = GoalMath.dayBounds(LocalDate.of(2026, 3, 8), zone) // spring forward
        assertEquals(23 * 60 * 60 * 1000L, e2 - s2)
        assertEquals(LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli(), start)
    }

    // NOTIF-001: reminder text lists unmet goals, or says all done, or defers to generic text.
    @Test
    fun reminderSummary() {
        val p = GoalMath.progress(stretches, goals, mapOf(1L to 1, 2L to 3, 4L to 1))
        assertEquals(listOf("Hamstring stretch 1 of 3"), GoalMath.reminderSummary(p))
        val allMet = GoalMath.progress(stretches, goals, mapOf(1L to 3, 2L to 3, 4L to 1))
        assertEquals(listOf(GoalMath.ALL_DONE), GoalMath.reminderSummary(allMet))
        assertNull(GoalMath.reminderSummary(emptyList()))
    }

    // NOTIF-010 / GOAL-005: reminders are suppressed only when goals exist and all are met.
    @Test
    fun allGoalsMet() {
        assertFalse(GoalMath.allGoalsMet(emptyList()))
        val oneShort = GoalMath.progress(stretches, goals, mapOf(1L to 3, 2L to 3))
        assertFalse(GoalMath.allGoalsMet(oneShort))
        val allMet = GoalMath.progress(stretches, goals, mapOf(1L to 3, 2L to 5, 4L to 1))
        assertTrue(GoalMath.allGoalsMet(allMet))
        // A stretch with completions but no goal doesn't matter either way.
        val withExtras = GoalMath.progress(stretches, goals, mapOf(1L to 3, 2L to 3, 3L to 9, 4L to 1))
        assertTrue(GoalMath.allGoalsMet(withExtras))
    }
}
