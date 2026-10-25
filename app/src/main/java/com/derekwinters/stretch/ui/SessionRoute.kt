package com.derekwinters.stretch.ui

import com.derekwinters.stretch.goals.ReminderOrigin

/**
 * SESS-001 / NOTIF-002 / HOME-009: the session screen's navigation route. Pure (no Android
 * types), so the one function both ways in use is unit tested on the JVM.
 */
object SessionRoute {
    const val BASE = "session"
    const val ARG_IDS = "ids"
    const val ARG_SCHEDULE = "schedule"
    const val ARG_AT = "at"

    /** The navigation pattern with the optional reminder origin arguments. */
    const val PATTERN = "$BASE?$ARG_IDS={$ARG_IDS}&$ARG_SCHEDULE={$ARG_SCHEDULE}&$ARG_AT={$ARG_AT}"

    /**
     * HOME-009 invariant: the route a reminder opens, whether from its notification or its Today
     * row. A reminder naming [stretchIds] (in the reminder's order), with its [scheduleName] and
     * [scheduledAt] (epoch milliseconds), opens with them as its own section; a reminder with no
     * stretches (every repeating schedule, SCHED-011), or missing either value, opens with no
     * named list, like "Stretch now".
     */
    fun of(stretchIds: List<Long>, scheduleName: String?, scheduledAt: Long): String =
        if (stretchIds.isNotEmpty() && scheduleName != null && scheduledAt >= 0) {
            "$BASE?$ARG_IDS=${ReminderOrigin.formatIds(stretchIds)}" +
                "&$ARG_SCHEDULE=${encode(scheduleName)}&$ARG_AT=$scheduledAt"
        } else {
            BASE
        }

    /**
     * Percent-encodes [text] as a route argument, as `android.net.Uri.encode` does: letters,
     * digits and `_-!.~'()*` stay as they are, every other UTF-8 byte becomes `%XX`.
     */
    internal fun encode(text: String): String = buildString {
        for (b in text.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (c < 0x80 && (ch.isLetterOrDigit() || ch in UNRESERVED)) {
                append(ch)
            } else {
                append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
            }
        }
    }

    private const val UNRESERVED = "_-!.~'()*"
    private const val HEX = "0123456789ABCDEF"
}
