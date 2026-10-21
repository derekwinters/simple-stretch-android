package com.derekwinters.stretch.widget

import com.derekwinters.stretch.goals.GoalRef
import com.derekwinters.stretch.goals.GoalRow
import com.derekwinters.stretch.goals.StretchRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Requirement IDs refer to docs/spec/widgets.md. */
class GoalRingTest {

    private fun row(id: Long, count: Int, done: Int) = GoalRow(id, "S$id", count, done)

    private fun assertClose(expected: Float, actual: Float) = assertEquals(expected, actual, 0.001f)

    // WIDGET-006: no goals set is a dotted track, a dimmed figure and no count.
    @Test
    fun noGoalsIsDottedDimmedAndWithoutCount() {
        val model = GoalRing.model(emptyList())
        assertTrue(model.arcs.isEmpty())
        assertTrue(model.dottedTrack)
        assertTrue(model.figureDimmed)
        assertEquals(RingCentre.None, model.centre)
    }

    // WIDGET-002/006: rows without a goal draw no arc; only stretches without goals is "no goals".
    @Test
    fun rowsWithoutGoalsAreIgnored() {
        val model = GoalRing.model(listOf(row(1, 0, 3), row(2, 0, 0)))
        assertTrue(model.dottedTrack)
        assertEquals(RingCentre.None, model.centre)
    }

    // WIDGET-002: one goal is a full ring with no gap.
    @Test
    fun oneGoalIsAFullRing() {
        val model = GoalRing.model(listOf(row(1, 3, 1)))
        assertEquals(1, model.arcs.size)
        assertClose(0f, model.arcs[0].startDeg)
        assertClose(360f, model.arcs[0].sweepDeg)
        assertClose(1f / 3f, model.arcs[0].fill)
        assertEquals(RingCentre.Count(1, 3), model.centre)
        assertFalse(model.figureDimmed)
    }

    // WIDGET-002: three goals share the ring equally, clockwise from the top, 8° apart, in the
    // order given (goals-screen order).
    @Test
    fun threeGoalsShareTheRingWithGaps() {
        val model = GoalRing.model(listOf(row(5, 2, 0), row(3, 2, 0), row(9, 2, 0)))
        assertEquals(listOf(5L, 3L, 9L), model.arcs.map { it.stretchId })
        model.arcs.forEachIndexed { i, arc ->
            assertClose(i * 120f + 4f, arc.startDeg)
            assertClose(112f, arc.sweepDeg)
        }
        // The visible gap between one arc's end and the next one's start is 8°.
        assertClose(8f, model.arcs[1].startDeg - (model.arcs[0].startDeg + model.arcs[0].sweepDeg))
        assertClose(8f, 360f + model.arcs[0].startDeg - (model.arcs[2].startDeg + model.arcs[2].sweepDeg))
    }

    // WIDGET-002: eight goals, 45° shares.
    @Test
    fun eightGoals() {
        val model = GoalRing.model((1L..8L).map { row(it, 1, 0) })
        assertEquals(8, model.arcs.size)
        assertClose(4f, model.arcs[0].startDeg)
        assertClose(37f, model.arcs[0].sweepDeg)
        assertClose(7 * 45f + 4f, model.arcs[7].startDeg)
        assertEquals(RingCentre.Count(0, 8), model.centre)
    }

    // WIDGET-006: nothing done yet dims the figure, shows only the track and reads 0/total.
    @Test
    fun nothingDoneYet() {
        val model = GoalRing.model(listOf(row(1, 2, 0), row(2, 2, 0), row(3, 2, 0)))
        assertTrue(model.figureDimmed)
        assertFalse(model.dottedTrack)
        assertTrue(model.arcs.all { it.fill == 0f })
        assertEquals(RingCentre.Count(0, 6), model.centre)
    }

    // WIDGET-003/004: partly met, from the mockup (Lean 1/2, Push 2/2, Butterfly 0/2).
    @Test
    fun partlyMet() {
        val model = GoalRing.model(listOf(row(1, 2, 1), row(2, 2, 2), row(3, 2, 0)))
        assertEquals(listOf(0.5f, 1f, 0f), model.arcs.map { it.fill })
        assertClose(56f, model.arcs[0].fillSweepDeg)
        assertEquals(RingCentre.Count(3, 6), model.centre)
        assertFalse(model.figureDimmed)
    }

    // WIDGET-003/004: progress above the count fills the arc and adds only the count.
    @Test
    fun progressAboveCountIsCapped() {
        val model = GoalRing.model(listOf(row(1, 3, 4), row(2, 2, 0)))
        assertEquals(1f, model.arcs[0].fill)
        assertEquals(RingCentre.Count(3, 5), model.centre)
    }

    // WIDGET-004: every goal met replaces the count with a check mark.
    @Test
    fun allMetShowsCheck() {
        val model = GoalRing.model(listOf(row(1, 2, 2), row(2, 2, 5), row(3, 1, 1)))
        assertEquals(RingCentre.Check, model.centre)
        assertTrue(model.arcs.all { it.fill == 1f })
        assertFalse(model.figureDimmed)
    }

    // WIDGET-009: built with the goals screen's calculation: current goals only, by name
    // ignoring case, stretches without a goal dropped, deleted stretches' goals ignored.
    @Test
    fun fromLogUsesGoalsScreenOrder() {
        val stretches = listOf(
            StretchRef(1, "butterfly"),
            StretchRef(2, "Achilles Wall Push"),
            StretchRef(3, "Achilles wall lean"),
            StretchRef(4, "Neck roll"),
        )
        val goals = listOf(GoalRef(1, 2), GoalRef(2, 2), GoalRef(3, 2), GoalRef(99, 5))
        val model = GoalRing.fromLog(stretches, goals, mapOf(3L to 1, 2L to 2, 4L to 7))
        assertEquals(listOf(3L, 2L, 1L), model.arcs.map { it.stretchId })
        assertEquals(RingCentre.Count(3, 6), model.centre)
    }

    // WIDGET-010 / WIDGET-009: the next redraw is the next local midnight.
    @Test
    fun nextRedrawIsNextLocalMidnight() {
        val zone = ZoneId.of("Europe/London")
        val now = ZonedDateTime.of(2026, 10, 21, 23, 59, 30, 0, zone).toInstant().toEpochMilli()
        val expected = LocalDate.of(2026, 10, 22).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, GoalRing.nextRedrawAt(now, zone))
    }

    // WIDGET-010: exactly at midnight the next redraw is the following midnight, and a day with
    // a daylight-saving change (25 hours in London on 2026-10-25) is handled.
    @Test
    fun nextRedrawAcrossDaylightSaving() {
        val zone = ZoneId.of("Europe/London")
        val midnight = LocalDate.of(2026, 10, 25).atStartOfDay(zone).toInstant().toEpochMilli()
        val next = LocalDate.of(2026, 10, 26).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(next, GoalRing.nextRedrawAt(midnight, zone))
        assertEquals(25L * 3_600_000L, next - midnight)
    }
}
