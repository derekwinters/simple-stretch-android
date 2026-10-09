package com.derekwinters.stretch.scheduling

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pure (Android-free) helpers for working out when reminders should fire.
 * Kept free of Android/Room types so they can be unit tested on the JVM.
 */
object DaysOfWeek {
    /** Bit 0 = Monday ... bit 6 = Sunday. */
    fun toMask(days: Set<DayOfWeek>): Int =
        days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

    fun fromMask(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

    val WEEKDAYS: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val ALL: Set<DayOfWeek> = DayOfWeek.entries.toSet()
}

object NextOccurrence {
    /**
     * Returns the first date-time strictly after [now] at [time] that falls on one of
     * [days] and is not in [skipped], or null if there is none within [maxDaysAhead] days
     * (e.g. no days selected).
     */
    fun compute(
        now: LocalDateTime,
        time: LocalTime,
        days: Set<DayOfWeek>,
        skipped: Set<LocalDate> = emptySet(),
        maxDaysAhead: Int = 400,
    ): LocalDateTime? {
        if (days.isEmpty()) return null
        val reminderTime = time.withSecond(0).withNano(0)
        var date = now.toLocalDate()
        repeat(maxDaysAhead + 1) {
            val candidate = LocalDateTime.of(date, reminderTime)
            if (candidate.isAfter(now) && date.dayOfWeek in days && date !in skipped) {
                return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }
}
