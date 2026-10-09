package com.derekwinters.stretch.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Requirement IDs refer to docs/spec/. */
class RepeatingScheduleTest {
    // 2026-10-05 is a Monday.
    private val monday = LocalDate.of(2026, 10, 5)

    private fun rule(start: String, end: String, every: Int, minute: Int) = RepeatRule(
        windowStartMinute = LocalTime.parse(start).let { it.hour * 60 + it.minute },
        windowEndMinute = LocalTime.parse(end).let { it.hour * 60 + it.minute },
        intervalMinutes = every,
        minutePastHour = minute,
    )

    private fun times(vararg t: String) = t.map { LocalTime.parse(it) }

    private val workday = rule("08:00", "17:00", 60, 50)

    // SCHED-012: the example from the user's request.
    @Test
    fun hourlyAtFiftyBetweenEightAndFive() {
        assertEquals(
            times("08:50", "09:50", "10:50", "11:50", "12:50", "13:50", "14:50", "15:50", "16:50"),
            RepeatingSlots.forDay(workday),
        )
    }

    // SCHED-012: the end time is inclusive.
    @Test
    fun slotExactlyAtEndFires() {
        val slots = RepeatingSlots.forDay(rule("08:00", "17:00", 60, 0))
        assertEquals(LocalTime.of(8, 0), slots.first())
        assertEquals(LocalTime.of(17, 0), slots.last())
        assertEquals(10, slots.size)
    }

    // SCHED-012: start is inclusive too, and a minute before the start rolls to the next hour.
    @Test
    fun minuteBeforeStartRollsToNextHour() {
        assertEquals(times("09:10", "10:10"), RepeatingSlots.forDay(rule("08:30", "10:10", 60, 10)))
        assertEquals(times("08:30", "09:30"), RepeatingSlots.forDay(rule("08:30", "10:10", 60, 30)))
    }

    // SCHED-012: interval that does not divide 60 only anchors the first slot.
    @Test
    fun nonDividingInterval() {
        assertEquals(
            times("08:10", "08:55", "09:40", "10:25", "11:10", "11:55"),
            RepeatingSlots.forDay(rule("08:00", "12:00", 45, 10)),
        )
    }

    // SCHED-012: end before start, or nothing fitting, means no slots; slots never cross midnight.
    @Test
    fun invalidOrEmptyWindows() {
        assertTrue(RepeatingSlots.forDay(rule("17:00", "08:00", 60, 0)).isEmpty())
        assertTrue(RepeatingSlots.forDay(rule("08:00", "08:30", 60, 50)).isEmpty())
        assertTrue(RepeatingSlots.forDay(rule("08:00", "17:00", 0, 0)).isEmpty())
        assertTrue(RepeatingSlots.forDay(rule("08:00", "17:00", 60, 60)).isEmpty())
        assertEquals(times("23:00", "23:59"), RepeatingSlots.forDay(rule("23:00", "23:59", 59, 0)))
        assertEquals(times("12:00"), RepeatingSlots.forDay(rule("12:00", "12:00", 60, 0)))
    }

    // SCHED-013: next slot strictly after now, same day.
    @Test
    fun nextSlotLaterToday() {
        val slots = RepeatingSlots.forDay(workday)
        assertEquals(
            monday.atTime(13, 50),
            NextOccurrence.computeAny(monday.atTime(13, 0), slots, DaysOfWeek.WEEKDAYS),
        )
        // Exactly at a slot: that one is due now, the next one is next.
        assertEquals(
            monday.atTime(14, 50),
            NextOccurrence.computeAny(monday.atTime(13, 50), slots, DaysOfWeek.WEEKDAYS),
        )
        // Before the first slot.
        assertEquals(
            monday.atTime(8, 50),
            NextOccurrence.computeAny(monday.atTime(6, 0), slots, DaysOfWeek.WEEKDAYS),
        )
    }

    // SCHED-013 + SCHED-003 invariant: after the last slot on Friday, next is Monday's first.
    @Test
    fun afterLastSlotFridayGoesToMonday() {
        val friday = monday.plusDays(4)
        assertEquals(
            monday.plusWeeks(1).atTime(8, 50),
            NextOccurrence.computeAny(friday.atTime(16, 50), RepeatingSlots.forDay(workday), DaysOfWeek.WEEKDAYS),
        )
    }

    // SCHED-013 + SKIP-004: a skipped day's slots are all passed over.
    @Test
    fun skippedTodayGoesToTomorrow() {
        assertEquals(
            monday.plusDays(1).atTime(8, 50),
            NextOccurrence.computeAny(
                monday.atTime(9, 0),
                RepeatingSlots.forDay(workday),
                DaysOfWeek.WEEKDAYS,
                skipped = setOf(monday),
            ),
        )
    }

    // SCHED-004 / SCHED-012: no days or no slots means no alarm.
    @Test
    fun noDaysOrNoSlots() {
        assertNull(NextOccurrence.computeAny(monday.atTime(9, 0), RepeatingSlots.forDay(workday), emptySet()))
        assertNull(NextOccurrence.computeAny(monday.atTime(9, 0), emptyList(), DaysOfWeek.ALL))
    }

    // computeAny with one time is the same as compute.
    @Test
    fun computeAnyMatchesComputeForOneTime() {
        val now = monday.atTime(11, 0)
        val ten = LocalTime.of(10, 0)
        assertEquals(
            NextOccurrence.compute(now, ten, DaysOfWeek.WEEKDAYS),
            NextOccurrence.computeAny(now, listOf(ten), DaysOfWeek.WEEKDAYS),
        )
    }

    // SCHED-005: alarm keys for reminders and repeating schedules never collide.
    @Test
    fun alarmKeysDoNotCollide() {
        val r = AlarmKeys.forReminder(3)
        val s = AlarmKeys.forRepeatingSchedule(3)
        assertTrue(r != s)
        assertFalse(AlarmKeys.isRepeatingSchedule(r))
        assertTrue(AlarmKeys.isRepeatingSchedule(s))
        assertEquals(3L, AlarmKeys.reminderId(r))
        assertNull(AlarmKeys.scheduleId(r))
        assertEquals(3L, AlarmKeys.scheduleId(s))
        assertNull(AlarmKeys.reminderId(s))
        assertEquals(-3, AlarmKeys.requestCode(s))
    }
}
