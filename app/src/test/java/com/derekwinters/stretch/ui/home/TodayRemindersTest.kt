package com.derekwinters.stretch.ui.home

import com.derekwinters.stretch.data.Reminder
import com.derekwinters.stretch.data.ReminderWithStretches
import com.derekwinters.stretch.data.Schedule
import com.derekwinters.stretch.data.ScheduleMode
import com.derekwinters.stretch.data.ScheduleWithReminders
import com.derekwinters.stretch.data.Stretch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Requirement IDs refer to docs/spec/screens.md and docs/spec/scheduling.md. */
class TodayRemindersTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    /** Monday 26 October 2026 at [local] in Berlin (CET, UTC+1), read through a fixed clock. */
    private fun nowAt(local: String): LocalDateTime {
        val instant = LocalDateTime.parse("2026-10-26T$local").atZone(berlin).toInstant()
        return LocalDateTime.now(Clock.fixed(instant, berlin))
    }

    private val everyDay = 0x7F

    private fun setTimes(id: Long, vararg times: String, enabled: Boolean = true, daysMask: Int = everyDay) =
        ScheduleWithReminders(
            schedule = Schedule(id = id, name = "Set $id", enabled = enabled, daysMask = daysMask),
            reminders = times.mapIndexed { i, t ->
                val time = LocalTime.parse(t)
                ReminderWithStretches(
                    reminder = Reminder(id = id * 100 + i, scheduleId = id, minuteOfDay = time.hour * 60 + time.minute),
                    stretches = listOf(Stretch(id = 1, name = "Neck rolls")),
                )
            },
        )

    /** 08:00 to 17:00, every 60 minutes at :00: slots 08:00 ... 17:00 (SCHED-012). */
    private fun repeating(id: Long) = ScheduleWithReminders(
        schedule = Schedule(
            id = id,
            name = "Repeat $id",
            daysMask = everyDay,
            mode = ScheduleMode.REPEATING,
            windowStartMinute = 8 * 60,
            windowEndMinute = 17 * 60,
            intervalMinutes = 60,
            minutePastHour = 0,
        ),
        reminders = emptyList(),
    )

    // HOME-002: with a fixed clock at 12:00, earlier set times are past (dimmed), later ones are
    // not, and a time exactly at now counts as past.
    @Test
    fun setTimesBeforeOrAtNowArePast() {
        val rows = TodayReminders.of(listOf(setTimes(1, "15:00", "09:00", "12:00")), nowAt("12:00"))
        assertEquals(listOf("09:00", "12:00", "15:00"), rows.map { it.time.toString() })
        assertEquals(listOf(true, true, false), rows.map { it.isPast })
        assertEquals(listOf("Neck rolls"), rows.first().stretchNames)
    }

    // HOME-002: a repeating schedule has one row, at its next slot today, not past.
    @Test
    fun repeatingScheduleShowsItsNextSlot() {
        val row = TodayReminders.of(listOf(repeating(2)), nowAt("12:30")).single()
        assertEquals(LocalTime.of(13, 0), row.time)
        assertFalse(row.isPast)
        assertEquals(5, row.slotsLeft)
        assertEquals("repeat-2", row.key)
    }

    // HOME-002: once all of a repeating schedule's slots have passed, its row shows the last slot
    // and is past (dimmed).
    @Test
    fun repeatingScheduleIsPastOnceAllSlotsHavePassed() {
        val row = TodayReminders.of(listOf(repeating(2)), nowAt("17:30")).single()
        assertEquals(LocalTime.of(17, 0), row.time)
        assertTrue(row.isPast)
        assertEquals(0, row.slotsLeft)
    }

    // HOME-002 / SCHED-016: the clock's zone decides the local time, so the same instant read in
    // another zone gives a different past state.
    @Test
    fun theClocksZoneDecidesWhatIsPast() {
        val instant = Instant.parse("2026-10-26T11:00:00Z") // 12:00 in Berlin, 07:00 in New York
        val schedules = listOf(setTimes(1, "09:00"))
        val inBerlin = TodayReminders.of(schedules, LocalDateTime.now(Clock.fixed(instant, berlin)))
        val inNewYork = TodayReminders.of(schedules, LocalDateTime.now(Clock.fixed(instant, ZoneId.of("America/New_York"))))
        assertTrue(inBerlin.single().isPast)
        assertFalse(inNewYork.single().isPast)
    }

    // HOME-009: a set-time row carries its reminder's stretch ids in the reminder's own order
    // (not sorted), so it opens the same session as its notification; a repeating schedule's row
    // names no stretches (SCHED-011), so its stretchIds is empty.
    @Test
    fun rowsCarryTheReminderStretchIdsInOrder() {
        val named = ScheduleWithReminders(
            schedule = Schedule(id = 1, name = "Set 1", daysMask = everyDay),
            reminders = listOf(
                ReminderWithStretches(
                    reminder = Reminder(id = 100, scheduleId = 1, minuteOfDay = 9 * 60),
                    stretches = listOf(
                        Stretch(id = 3, name = "Hamstring stretch"),
                        Stretch(id = 1, name = "Neck rolls"),
                        Stretch(id = 2, name = "Calf stretch"),
                    ),
                ),
            ),
        )
        val rows = TodayReminders.of(listOf(named, repeating(2)), nowAt("12:30"))
        assertEquals(listOf(3L, 1L, 2L), rows.single { it.key == "reminder-100" }.stretchIds)
        assertEquals(emptyList<Long>(), rows.single { it.key == "repeat-2" }.stretchIds)
    }

    // HOME-002: disabled schedules and schedules not on today's weekday are left out.
    @Test
    fun disabledAndOtherDaySchedulesAreLeftOut() {
        val mondayOnly = 1 // bit 0 = Monday
        val tuesdayOnly = 2
        val rows = TodayReminders.of(
            listOf(
                setTimes(1, "10:00", enabled = false),
                setTimes(2, "11:00", daysMask = tuesdayOnly),
                setTimes(3, "13:00", daysMask = mondayOnly),
            ),
            nowAt("12:00"),
        )
        assertEquals(listOf("reminder-300"), rows.map { it.key })
    }
}
