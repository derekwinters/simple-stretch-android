package com.derekwinters.stretch.goals

/** Which top section the session screen shows (SESS-002); null in [SessionLayout.top] means none. */
enum class SessionTop { FROM_REMINDER, GOALS_SHORT }

/**
 * SESS-002: the session screen's two sections, fixed when the screen opens (SESS-005). Every
 * stretch is in exactly one of [topEntries] and [others]; [topEntries] is empty when [top] is null.
 */
data class SessionLayout(
    val top: SessionTop?,
    val topEntries: List<SessionEntry>,
    val others: List<SessionEntry>,
)

/** SESS-002: section headings. */
object SessionHeadings {
    const val GOALS_SHORT = "Goals still short today"
    const val OTHERS = "Other stretches"

    /** e.g. "From your 9:00 Morning reminder"; [time] is already formatted for the user. */
    fun fromReminder(time: String, scheduleName: String): String = "From your $time $scheduleName reminder"
}

/**
 * Pure helpers for what a reminder carries into the session (SESS-001, NOTIF-002/003, NOTIF-008).
 */
object ReminderOrigin {
    /**
     * NOTIF-008 invariant: the time a reminder was first scheduled for. A snooze carries it as
     * [extra]; only an alarm armed before that extra existed falls back to its [triggerAt].
     */
    fun scheduledAt(extra: Long?, triggerAt: Long): Long = extra ?: triggerAt

    /** SESS-001: the named stretch ids as a route argument, in the reminder's order. */
    fun formatIds(ids: List<Long>): String = ids.joinToString(",")

    /** Inverse of [formatIds]; anything unparsable is ignored. */
    fun parseIds(text: String?): List<Long> =
        text.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
}
