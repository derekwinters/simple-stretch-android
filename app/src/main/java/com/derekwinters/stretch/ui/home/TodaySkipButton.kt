package com.derekwinters.stretch.ui.home

/**
 * SKIP-001 / HOME-002: the text button at the right end of the home screen's "Today" heading
 * row. [skipsOnTap] is true for "Skip today" and false for "Resume today".
 */
data class TodaySkipButton(val label: String, val enabled: Boolean, val skipsOnTap: Boolean) {
    companion object {
        /**
         * HOME-002 invariant: a skipped today always yields an enabled "Resume today"; nothing
         * else is consulted. Otherwise "Skip today", enabled only while a reminder is left today.
         */
        fun of(todaySkipped: Boolean, reminders: List<TodayReminder>): TodaySkipButton =
            if (todaySkipped) {
                TodaySkipButton("Resume today", enabled = true, skipsOnTap = false)
            } else {
                TodaySkipButton("Skip today", enabled = reminders.any { !it.isPast }, skipsOnTap = true)
            }
    }
}
