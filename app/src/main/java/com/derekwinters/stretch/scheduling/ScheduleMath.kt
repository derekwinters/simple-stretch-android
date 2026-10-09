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
    ): LocalDateTime? = computeAny(now, listOf(time), days, skipped, maxDaysAhead)

    /**
     * Like [compute], for a day with several times (a repeating schedule's slots, SCHED-013):
     * the earliest of [times] strictly after [now], on a selected, unskipped day.
     */
    fun computeAny(
        now: LocalDateTime,
        times: List<LocalTime>,
        days: Set<DayOfWeek>,
        skipped: Set<LocalDate> = emptySet(),
        maxDaysAhead: Int = 400,
    ): LocalDateTime? {
        if (days.isEmpty() || times.isEmpty()) return null
        val sorted = times.map { it.withSecond(0).withNano(0) }.distinct().sorted()
        var date = now.toLocalDate()
        repeat(maxDaysAhead + 1) {
            if (date.dayOfWeek in days && date !in skipped) {
                for (t in sorted) {
                    val candidate = LocalDateTime.of(date, t)
                    if (candidate.isAfter(now)) return candidate
                }
            }
            date = date.plusDays(1)
        }
        return null
    }
}

/** A repeating schedule's timing (SCHED-011), as minutes after local midnight. */
data class RepeatRule(
    val windowStartMinute: Int,
    val windowEndMinute: Int,
    val intervalMinutes: Int,
    val minutePastHour: Int,
) {
    companion object {
        const val MIN_INTERVAL = 5
        const val MAX_INTERVAL = 720
        const val DEFAULT_INTERVAL = 60
        const val DEFAULT_MINUTE = 0
        const val DEFAULT_START = 8 * 60
        const val DEFAULT_END = 17 * 60
    }
}

object RepeatingSlots {
    /**
     * SCHED-012: the first time at or after the start whose minute is [RepeatRule.minutePastHour],
     * then every [RepeatRule.intervalMinutes], while at or before the end (end inclusive). Never
     * crosses midnight. Invalid rules (end before start, interval or minute out of range) give
     * no slots.
     */
    fun forDay(rule: RepeatRule): List<LocalTime> {
        val start = rule.windowStartMinute
        val end = rule.windowEndMinute
        val interval = rule.intervalMinutes
        val minute = rule.minutePastHour
        if (start !in 0 until MINUTES_PER_DAY || end !in 0 until MINUTES_PER_DAY) return emptyList()
        if (end < start) return emptyList()
        if (interval !in RepeatRule.MIN_INTERVAL..RepeatRule.MAX_INTERVAL) return emptyList()
        if (minute !in 0..59) return emptyList()

        var slot = (start / 60) * 60 + minute
        if (slot < start) slot += 60
        val result = mutableListOf<LocalTime>()
        while (slot <= end) {
            result += LocalTime.of(slot / 60, slot % 60)
            slot += interval
        }
        return result
    }

    private const val MINUTES_PER_DAY = 24 * 60
}

/**
 * SCHED-005: one alarm key per alarm. A reminder time's key is its (positive) reminder id; a
 * repeating schedule's key is its negated schedule id. Room ids start at 1, so they never
 * collide. The key doubles as PendingIntent request code and notification id.
 */
object AlarmKeys {
    fun forReminder(reminderId: Long): Long {
        require(reminderId > 0) { "reminder ids start at 1" }
        return reminderId
    }

    fun forRepeatingSchedule(scheduleId: Long): Long {
        require(scheduleId > 0) { "schedule ids start at 1" }
        return -scheduleId
    }

    fun isRepeatingSchedule(key: Long): Boolean = key < 0

    fun reminderId(key: Long): Long? = if (key > 0) key else null

    fun scheduleId(key: Long): Long? = if (key < 0) -key else null

    /** Request code / notification id. Ids are far below Int.MAX_VALUE in practice. */
    fun requestCode(key: Long): Int = key.toInt()
}
