package com.derekwinters.stretch.notifications

/** A button on a reminder notification. */
enum class ReminderAction { START, SNOOZE }

/**
 * NOTIF-009: the reminder's buttons and the broadcast actions [NotificationActionReceiver]
 * handles, kept pure so they are unit tested without Android.
 */
object ReminderActions {
    /** NOTIF-009: exactly Start then Snooze 5 min; the third slot stays empty. */
    val BUTTONS: List<ReminderAction> = listOf(ReminderAction.START, ReminderAction.SNOOZE)

    /**
     * NOTIF-009 invariant: only Snooze is handled. The old Skip today action (NOTIF-004 retired),
     * still carried by notifications posted before it was removed, is ignored.
     */
    private val HANDLED: Set<String> = setOf(NotificationActionReceiver.ACTION_SNOOZE)

    fun isHandled(action: String?): Boolean = action in HANDLED
}
