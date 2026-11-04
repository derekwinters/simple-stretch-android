package com.derekwinters.stretch.ui.home

import com.derekwinters.stretch.goals.GoalRef
import com.derekwinters.stretch.goals.StretchRef
import com.derekwinters.stretch.widget.GoalRing
import com.derekwinters.stretch.widget.LauncherIconColors
import com.derekwinters.stretch.widget.RingCentre
import com.derekwinters.stretch.widget.WidgetPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Requirement IDs refer to docs/spec/screens.md (HOME-) and docs/spec/widgets.md (WIDGET-). Home's
 * ring is the widget's ring (HOME-006 invariant): built by GoalRing.fromLog, described and
 * coloured here.
 */
class HomeRingTest {

    private val stretches = listOf(
        StretchRef(1, "Neck rolls"),
        StretchRef(2, "Hamstring stretch"),
        StretchRef(3, "Calf stretch"),
        StretchRef(4, "Shoulder shrugs"),
    )
    private val goals = listOf(GoalRef(1, 2), GoalRef(2, 3), GoalRef(3, 3))

    // HOME-006 / WIDGET-004: Calf 1/3, Hamstring 2/3, Neck rolls 1/2 gives 4/8 in the centre,
    // one arc per goal; a stretch without a goal adds nothing.
    @Test
    fun ringShowsDoneOverTotal() {
        val ring = GoalRing.fromLog(stretches, goals, mapOf(1L to 1, 2L to 2, 3L to 1, 4L to 5))
        assertEquals(RingCentre.Count(done = 4, total = 8), ring.centre)
        assertEquals(listOf(3L, 2L, 1L), ring.arcs.map { it.stretchId })
        assertEquals("Today's goals: 4 of 8 done", HomeRing.description(ring))
    }

    // HOME-006 / WIDGET-006: no goals is the dotted empty ring with the dimmed figure, no count,
    // and a description that says so.
    @Test
    fun noGoalsIsTheDottedEmptyRing() {
        val ring = GoalRing.fromLog(stretches, emptyList(), mapOf(1L to 3))
        assertTrue(ring.dottedTrack)
        assertTrue(ring.figureDimmed)
        assertEquals(RingCentre.None, ring.centre)
        assertTrue(HomeRing.showsSetAGoal(ring))
        assertEquals("No daily goals set", HomeRing.description(ring))
    }

    // HOME-006: "Set a goal" shows only while no goal is set.
    @Test
    fun setAGoalOnlyWithoutGoals() {
        val ring = GoalRing.fromLog(stretches, goals, emptyMap())
        assertEquals(false, HomeRing.showsSetAGoal(ring))
        assertEquals("Today's goals: 0 of 8 done", HomeRing.description(ring))
    }

    // HOME-006 / WIDGET-004: every goal met shows the check mark and says so.
    @Test
    fun allMetSaysSo() {
        val ring = GoalRing.fromLog(stretches, goals, mapOf(1L to 2, 2L to 4, 3L to 3))
        assertEquals(RingCentre.Check, ring.centre)
        assertEquals("Today's goals: all met", HomeRing.description(ring))
    }

    // HOME-006: the theme's colours map to the ring's parts with the same roles and alphas the
    // widget uses for dynamic colour (WIDGET-008): primary for arcs, onSurface for text and the
    // track. The figure, dimmed figure and mat are the launcher icon's (HOME-006 invariant).
    @Test
    fun themeColoursUseTheWidgetsRoles() {
        val primary = 0xFF2E7D6B.toInt()
        val surface = 0xFFFFFBFE.toInt()
        val onSurface = 0xFF1C1B1F.toInt()
        val light = WidgetPalette.fromTheme(primary, surface, onSurface, dark = false)
        assertEquals(primary, light.arc)
        assertEquals(onSurface, light.text)
        assertEquals(LauncherIconColors.MAT, light.mat)
        assertEquals(WidgetPalette.withAlpha(onSurface, 0.14f), light.track)
        assertEquals(LauncherIconColors.FIGURE, light.figure)
        assertEquals(WidgetPalette.withAlpha(LauncherIconColors.FIGURE, 0.40f), light.dimmedFigure)
        val dark = WidgetPalette.fromTheme(primary, surface, onSurface, dark = true)
        assertEquals(WidgetPalette.withAlpha(onSurface, 0.18f), dark.track)
    }
}
