package com.derekwinters.stretch.ui.home

import com.derekwinters.stretch.scheduling.RepeatRule
import com.derekwinters.stretch.ui.SessionRoute
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Requirement IDs refer to docs/spec/screens.md and docs/spec/sessions.md. */
class TodayRowSessionRouteTest {

    private val today = LocalDate.of(2026, 3, 10)
    private val zone = ZoneId.of("Europe/London")
    private val nineAm = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun setTimeRow(ids: List<Long>, name: String, time: LocalTime, isPast: Boolean = false) =
        TodayReminder(
            key = "reminder-1",
            time = time,
            scheduleName = name,
            stretchNames = ids.map { "Stretch $it" },
            isPast = isPast,
            stretchIds = ids,
        )

    // HOME-009 invariant: a set-time row opens exactly what its notification would. The
    // notification path (MainActivity, NOTIF-002) builds the route from the intent's stretch
    // ids, schedule name and scheduled time; the row builds it from the same values.
    @Test
    fun setTimeRowOpensSameRouteAsItsNotification() {
        val row = setTimeRow(listOf(3L, 1L, 2L), "Morning", LocalTime.of(9, 0))

        val fromNotification = SessionRoute.of(listOf(3L, 1L, 2L), "Morning", nineAm)

        assertEquals(fromNotification, row.sessionRoute(today, zone))
    }

    // HOME-009 / SESS-001: the stretch ids keep the reminder's order and the schedule name is
    // encoded as a route argument the session screen decodes back.
    @Test
    fun setTimeRouteCarriesIdsInOrderNameAndScheduledTime() {
        val row = setTimeRow(listOf(3L, 1L, 2L), "Desk & stretch", LocalTime.of(9, 0))

        assertEquals(
            "session?ids=3,1,2&schedule=Desk%20%26%20stretch&at=$nineAm",
            row.sessionRoute(today, zone),
        )
    }

    // HOME-009: every row is tappable; a past row opens the same route as an upcoming one.
    @Test
    fun pastRowOpensItsReminderToo() {
        val row = setTimeRow(listOf(5L), "Morning", LocalTime.of(9, 0), isPast = true)

        assertEquals(SessionRoute.of(listOf(5L), "Morning", nineAm), row.sessionRoute(today, zone))
    }

    // HOME-009 / SCHED-011: a repeating schedule's reminders name no stretches, so its row, like
    // its notification, opens the session with no named list ("Goals still short today").
    @Test
    fun repeatingRowOpensNoNamedListLikeItsNotification() {
        val row = TodayReminder(
            key = "repeat-7",
            time = LocalTime.of(9, 0),
            scheduleName = "Workday",
            stretchNames = emptyList(),
            isPast = false,
            repeatRule = RepeatRule(windowStartMinute = 480, windowEndMinute = 1020, intervalMinutes = 60, minutePastHour = 50),
            slotsLeft = 8,
        )

        val fromNotification = SessionRoute.of(emptyList(), "Workday", nineAm)

        assertEquals(fromNotification, row.sessionRoute(today, zone))
        assertEquals("session", row.sessionRoute(today, zone))
    }
}
