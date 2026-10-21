package com.derekwinters.stretch.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** Requirement IDs refer to docs/spec/skipping.md and docs/spec/screens.md. */
class TodaySkipButtonTest {

    private fun reminder(time: String, isPast: Boolean, slotsLeft: Int = 0) =
        TodayReminder(
            key = "r-$time",
            time = LocalTime.parse(time),
            scheduleName = "Workday",
            stretchNames = emptyList(),
            isPast = isPast,
            slotsLeft = slotsLeft,
        )

    // SKIP-001 / HOME-002: reminders left today -> an enabled "Skip today" that skips.
    @Test
    fun remindersLeftShowsEnabledSkip() {
        val b = TodaySkipButton.of(
            todaySkipped = false,
            reminders = listOf(reminder("09:00", isPast = true), reminder("15:00", isPast = false)),
        )
        assertEquals("Skip today", b.label)
        assertTrue(b.enabled)
        assertTrue(b.skipsOnTap)
    }

    // SKIP-001 / HOME-002: nothing left today -> "Skip today" shown but disabled.
    @Test
    fun noRemindersLeftShowsDisabledSkip() {
        val allPast = TodaySkipButton.of(
            todaySkipped = false,
            reminders = listOf(reminder("09:00", isPast = true), reminder("10:00", isPast = true)),
        )
        assertEquals("Skip today", allPast.label)
        assertFalse(allPast.enabled)

        val none = TodaySkipButton.of(todaySkipped = false, reminders = emptyList())
        assertEquals("Skip today", none.label)
        assertFalse(none.enabled)
    }

    // HOME-002 invariant: when today is skipped the row always shows an enabled "Resume today",
    // whatever is (or is not) left today.
    @Test
    fun skippedTodayAlwaysShowsResume() {
        val cases = listOf(
            emptyList(),
            listOf(reminder("09:00", isPast = true)),
            listOf(reminder("15:00", isPast = false)),
        )
        for (reminders in cases) {
            val b = TodaySkipButton.of(todaySkipped = true, reminders = reminders)
            assertEquals("Resume today", b.label)
            assertTrue(b.enabled)
            assertFalse(b.skipsOnTap)
        }
    }

    // HOME-002: a repeating schedule with slots still to come counts as a reminder left today.
    @Test
    fun repeatingScheduleWithSlotsLeftEnablesSkip() {
        val b = TodaySkipButton.of(
            todaySkipped = false,
            reminders = listOf(reminder("10:50", isPast = false, slotsLeft = 6)),
        )
        assertTrue(b.enabled)
    }

    // HOME-008 retired: the home state carries no skip-card "closed today" flag, so nothing
    // stored per device can hide the skip/resume control.
    @Test
    fun homeStateHasNoSkipCardClosedFlag() {
        val names = HomeState::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(names.none { "closed" in it || "skipcard" in it })
    }
}
