package com.derekwinters.stretch.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Requirement IDs refer to docs/spec/screens.md (HOME-012). */
class NavMotionTest {

    private val tabs = TopTab.entries.map { it.route }
    private val fullWindow = listOf(
        Routes.SCHEDULE,
        Routes.SKIPS,
        Routes.STRETCHES,
        Routes.TREND_DAY,
        Routes.SESSION,
    )

    // HOME-012: every tab-to-tab move, either way, is a fade through.
    @Test
    fun tabToTabFadesThrough() {
        for (from in tabs) for (to in tabs) for (pop in listOf(false, true)) {
            assertEquals("$from -> $to pop=$pop", NavMotion.FADE_THROUGH, NavMotion.of(from, to, pop))
        }
    }

    // HOME-012: opening a full-window screen, from a tab or from another full-window screen
    // (a notification or the widget replacing it), is shared axis X forward.
    @Test
    fun openingAFullWindowScreenIsSharedAxisForward() {
        for (from in tabs + fullWindow) for (to in fullWindow) {
            assertEquals("$from -> $to", NavMotion.SHARED_AXIS_FORWARD, NavMotion.of(from, to, pop = false))
        }
    }

    // HOME-012: Back from a full-window screen plays shared axis X in reverse.
    @Test
    fun backFromAFullWindowScreenIsSharedAxisBack() {
        for (from in fullWindow) for (to in tabs + fullWindow) {
            assertEquals("$from -> $to", NavMotion.SHARED_AXIS_BACK, NavMotion.of(from, to, pop = true))
        }
    }

    // HOME-012 first invariant: the outgoing screen has fully faded out before the incoming one
    // starts to fade in, so no two screens are ever part-transparent at once; and the whole
    // motion takes Material's 300 ms, well inside the library's 700 ms cross-fade.
    @Test
    fun fadesNeverOverlap() {
        assertTrue(NavMotion.OUTGOING_FADE_MILLIS <= NavMotion.INCOMING_FADE_DELAY_MILLIS)
        assertEquals(
            NavMotion.DURATION_MILLIS,
            NavMotion.INCOMING_FADE_DELAY_MILLIS + NavMotion.INCOMING_FADE_MILLIS,
        )
        assertEquals(300, NavMotion.DURATION_MILLIS)
        assertEquals(105, NavMotion.OUTGOING_FADE_MILLIS)
        assertEquals(0.92f, NavMotion.FADE_THROUGH_SCALE, 0f)
    }
}
