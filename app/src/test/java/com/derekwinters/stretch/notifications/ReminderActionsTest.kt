package com.derekwinters.stretch.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Requirement IDs refer to docs/spec/notifications.md and docs/spec/skipping.md. */
class ReminderActionsTest {

    // NOTIF-009: a reminder shows exactly two buttons, Start then Snooze 5 min.
    // NOTIF-004 / SKIP-005 retired: there is no Skip today button.
    @Test
    fun reminderButtonsAreStartThenSnoozeOnly() {
        assertEquals(listOf(ReminderAction.START, ReminderAction.SNOOZE), ReminderActions.BUTTONS)
    }

    // NOTIF-009 invariant: the receiver handles only Snooze; the old Skip today action, still
    // carried by notifications posted before the update, is ignored (NOTIF-004 retired).
    @Test
    fun receiverHandlesSnoozeButNotTheOldSkipTodayAction() {
        assertTrue(ReminderActions.isHandled(NotificationActionReceiver.ACTION_SNOOZE))
        assertFalse(ReminderActions.isHandled("com.derekwinters.stretch.action.SKIP_TODAY"))
        assertFalse(ReminderActions.isHandled(null))
    }
}
