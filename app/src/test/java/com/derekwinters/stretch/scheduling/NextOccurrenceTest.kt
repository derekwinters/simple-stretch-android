package com.derekwinters.stretch.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Requirement IDs refer to docs/spec/. */
class NextOccurrenceTest {
    // 2026-10-05 is a Monday.
    private val monday = LocalDate.of(2026, 10, 5)
    private val ten = LocalTime.of(10, 0)

    // SCHED-003
    @Test
    fun laterTodayWhenDayMatches() {
        val now = monday.atTime(9, 0)
        assertEquals(monday.atTime(10, 0), NextOccurrence.compute(now, ten, DaysOfWeek.WEEKDAYS))
    }

    // SCHED-003: "strictly after now" - an occurrence at exactly now is already due, not next.
    @Test
    fun exactlyAtTimeMovesToNextDay() {
        val now = monday.atTime(10, 0)
        assertEquals(
            monday.plusDays(1).atTime(10, 0),
            NextOccurrence.compute(now, ten, DaysOfWeek.WEEKDAYS),
        )
    }

    // SCHED-003
    @Test
    fun timeAlreadyPassedMovesToNextMatchingDay() {
        val now = monday.atTime(11, 0)
        assertEquals(
            monday.plusDays(1).atTime(10, 0),
            NextOccurrence.compute(now, ten, DaysOfWeek.WEEKDAYS),
        )
    }

    // SCHED-003 invariant: never on a day the schedule doesn't select.
    @Test
    fun fridayEveningWeekdayScheduleJumpsToMonday() {
        val friday = monday.plusDays(4)
        val now = friday.atTime(18, 0)
        assertEquals(
            monday.plusWeeks(1).atTime(10, 0),
            NextOccurrence.compute(now, ten, DaysOfWeek.WEEKDAYS),
        )
    }

    // SCHED-003 invariant: never on a day the schedule doesn't select.
    @Test
    fun weekendOnlyScheduleFromMonday() {
        val now = monday.atTime(8, 0)
        assertEquals(
            monday.plusDays(5).atTime(10, 0),
            NextOccurrence.compute(now, ten, DaysOfWeek.WEEKEND),
        )
    }

    // SKIP-001, SKIP-004
    @Test
    fun skippedTodayGoesToTomorrow() {
        val now = monday.atTime(8, 0)
        assertEquals(
            monday.plusDays(1).atTime(10, 0),
            NextOccurrence.compute(now, ten, DaysOfWeek.ALL, skipped = setOf(monday)),
        )
    }

    // SKIP-002, SKIP-004
    @Test
    fun multipleSkippedDaysAreAllAvoided() {
        val now = monday.atTime(8, 0)
        val skipped = setOf(monday, monday.plusDays(1), monday.plusDays(2))
        assertEquals(
            monday.plusDays(3).atTime(10, 0),
            NextOccurrence.compute(now, ten, DaysOfWeek.WEEKDAYS, skipped),
        )
    }

    // SKIP-004
    @Test
    fun skippingNonScheduledDayHasNoEffect() {
        val now = monday.atTime(8, 0)
        val sunday = monday.minusDays(1).plusWeeks(1)
        assertEquals(
            monday.atTime(10, 0),
            NextOccurrence.compute(now, ten, DaysOfWeek.WEEKDAYS, setOf(sunday)),
        )
    }

    // SKIP-004, SKIP-006: skipping a date moves past it, the schedule's own days still apply.
    @Test
    fun skippedSingleDayScheduleRollsToFollowingWeek() {
        val now = monday.atTime(8, 0)
        assertEquals(
            monday.plusWeeks(1).atTime(10, 0),
            NextOccurrence.compute(now, ten, setOf(DayOfWeek.MONDAY), setOf(monday)),
        )
    }

    // SCHED-004: a schedule with no days never schedules anything.
    @Test
    fun noDaysReturnsNull() {
        assertNull(NextOccurrence.compute(monday.atTime(8, 0), ten, emptySet()))
    }

    // SKIP-004
    @Test
    fun everyCandidateSkippedReturnsNull() {
        val now = monday.atTime(8, 0)
        val skipped = (0L..10L).map { monday.plusDays(it) }.toSet()
        assertNull(NextOccurrence.compute(now, ten, DaysOfWeek.ALL, skipped, maxDaysAhead = 10))
    }

    // SCHED-009
    @Test
    fun secondsInReminderTimeAreIgnored() {
        val now = monday.atTime(10, 0, 30)
        assertEquals(
            monday.plusDays(1).atTime(10, 0),
            NextOccurrence.compute(now, LocalTime.of(10, 0, 45), DaysOfWeek.ALL),
        )
    }

    // SCHED-001: selected days persist exactly as picked.
    @Test
    fun daysMaskRoundTrips() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        val mask = DaysOfWeek.toMask(days)
        assertEquals(0b1000101, mask)
        assertEquals(days, DaysOfWeek.fromMask(mask))
        assertEquals(DaysOfWeek.ALL, DaysOfWeek.fromMask(0b1111111))
        assertEquals(emptySet<DayOfWeek>(), DaysOfWeek.fromMask(0))
    }
}
