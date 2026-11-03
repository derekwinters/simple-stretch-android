package com.derekwinters.stretch.ui.home

import com.derekwinters.stretch.data.Reminder
import com.derekwinters.stretch.data.ReminderWithStretches
import com.derekwinters.stretch.data.Schedule
import com.derekwinters.stretch.data.ScheduleMode
import com.derekwinters.stretch.data.ScheduleWithReminders
import com.derekwinters.stretch.data.Stretch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Requirement IDs refer to docs/spec/screens.md. The Today list's split into upcoming rows and
 * greyed past rows (HOME-002), with a fixed clock (SCHED-016).
 */
class TodayListTest {

    private val zone = ZoneId.of("America/New_York")

    /** Friday 16 October 2026 at [local], read through a fixed clock. */
    private fun nowAt(local: String): LocalDateTime {
        val instant = LocalDateTime.parse("2026-10-16T$local").atZone(zone).toInstant()
        return LocalDateTime.now(Clock.fixed(instant, zone))
    }

    private val everyDay = 0x7F

    private val deskBreaks = ScheduleWithReminders(
        schedule = Schedule(id = 1, name = "Desk breaks", daysMask = everyDay),
        reminders = listOf("09:00", "12:30", "15:30").mapIndexed { i, t ->
            val time = LocalTime.parse(t)
            ReminderWithStretches(
                reminder = Reminder(id = 10L + i, scheduleId = 1, minuteOfDay = time.hour * 60 + time.minute),
                stretches = listOf(Stretch(id = 1, name = "Neck rolls")),
            )
        },
    )

    /** Every 60 minutes at :50 from 8:00 to 17:00: slots 8:50 ... 16:50 (SCHED-012). */
    private val workday = ScheduleWithReminders(
        schedule = Schedule(
            id = 2,
            name = "Workday",
            daysMask = everyDay,
            mode = ScheduleMode.REPEATING,
            windowStartMinute = 8 * 60,
            windowEndMinute = 17 * 60,
            intervalMinutes = 60,
            minutePastHour = 50,
        ),
        reminders = emptyList(),
    )

    private fun listAt(local: String, skipped: Boolean = false): TodayList =
        TodayList.of(TodayReminders.of(listOf(deskBreaks, workday), nowAt(local)), todaySkipped = skipped)

    private fun TodayList.upcomingTimes() = upcoming.map { it.reminder.time.toString() }
    private fun TodayList.pastTimes() = past.map { it.reminder.time.toString() }

    // HOME-002: upcoming reminders first, in time order; today's past reminders after them, in
    // time order, greyed. At 9:40 the 9:00 reminder has passed and the repeating schedule's next
    // slot is 9:50 (the showcase moment, as in the mockup).
    @Test
    fun upcomingFirstThenPastGreyed() {
        val list = listAt("09:40")
        assertEquals(listOf("09:50", "12:30", "15:30"), list.upcomingTimes())
        assertEquals(listOf("09:00"), list.pastTimes())
        assertTrue(list.upcoming.none { it.greyed })
        assertTrue(list.past.all { it.greyed })
    }

    // HOME-002: a repeating schedule is one row at its next slot among the upcoming rows until
    // its last slot today (16:50), then moves to the greyed past rows at that last slot.
    @Test
    fun repeatingScheduleMovesToPastAfterItsLastSlot() {
        val before = listAt("16:49")
        assertEquals(listOf("16:50"), before.upcomingTimes())
        assertEquals("repeat-2", before.upcoming.single().reminder.key)
        assertEquals(1, before.upcoming.single().reminder.slotsLeft)

        val after = listAt("16:50")
        assertEquals(emptyList<String>(), after.upcomingTimes())
        assertEquals(listOf("09:00", "12:30", "15:30", "16:50"), after.pastTimes())
        assertEquals("repeat-2", after.past.last().reminder.key)
        assertTrue(after.past.all { it.greyed })
    }

    // HOME-002: a set time exactly at now is past.
    @Test
    fun setTimeAtNowIsPast() {
        val list = listAt("12:30")
        assertEquals(listOf("09:00", "12:30"), list.pastTimes())
        assertEquals(listOf("12:50", "15:30"), list.upcomingTimes())
    }

    // HOME-002: when today is skipped every row is greyed, upcoming and past alike, and the two
    // groups keep their order.
    @Test
    fun skippedTodayGreysEveryRow() {
        val list = listAt("09:40", skipped = true)
        assertEquals(listOf("09:50", "12:30", "15:30"), list.upcomingTimes())
        assertEquals(listOf("09:00"), list.pastTimes())
        assertTrue((list.upcoming + list.past).all { it.greyed })
    }

    // HOME-002 invariant: the split depends only on the rows and the time read from the clock,
    // so the same rows at a later time move a reminder below the divider.
    @Test
    fun aReminderMovesBelowTheDividerAtItsTime() {
        assertEquals(listOf("09:00"), listAt("12:29").pastTimes())
        assertEquals(listOf("09:00", "12:30"), listAt("12:30").pastTimes())
    }

    // HOME-002: no reminders today gives two empty groups.
    @Test
    fun noRemindersGivesAnEmptyList() {
        val list = TodayList.of(emptyList(), todaySkipped = false)
        assertTrue(list.isEmpty)
        assertTrue(TodayList.of(emptyList(), todaySkipped = true).isEmpty)
    }
}
