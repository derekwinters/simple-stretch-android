package com.derekwinters.stretch.scheduling

import org.junit.Assert.assertEquals
import org.junit.Test

/** Requirement IDs refer to docs/spec/scheduling.md. */
class ExactAlarmFallbackTest {
    private var exact = 0
    private var inexact = 0
    private var refused = 0

    private fun arm(exactAllowed: Boolean, exactThrows: Boolean = false) = armAlarm(
        exactAllowed = exactAllowed,
        setExact = {
            exact++
            if (exactThrows) throw SecurityException("exact alarm refused")
        },
        setInexact = { inexact++ },
        onExactRefused = { refused++ },
    )

    // SCHED-007: exact when the app may schedule exact alarms.
    @Test
    fun exactAllowedSetsExactOnly() {
        arm(exactAllowed = true)
        assertEquals(1, exact)
        assertEquals(0, inexact)
    }

    // SCHED-007: without the grant, alarms fall back to inexact.
    @Test
    fun exactNotAllowedSetsInexactOnly() {
        arm(exactAllowed = false)
        assertEquals(0, exact)
        assertEquals(1, inexact)
    }

    // SCHED-007 invariant: a refused exact alarm is replaced by an inexact one, never dropped.
    @Test
    fun refusedExactFallsBackToInexactOnce() {
        arm(exactAllowed = true, exactThrows = true)
        assertEquals(1, exact)
        assertEquals(1, inexact)
        assertEquals(1, refused)
    }
}
