package com.derekwinters.stretch

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/** Requirement IDs refer to docs/spec/scheduling.md. */
class AppClockTest {

    private lateinit var savedDefault: TimeZone

    @Before
    fun saveDefaultZone() {
        savedDefault = TimeZone.getDefault()
    }

    @After
    fun restoreDefaultZone() {
        TimeZone.setDefault(savedDefault)
    }

    // SCHED-010 invariant / SCHED-016: the clock is built once, then the device's zone changes;
    // the clock reports the new zone, never the one it was built with.
    @Test
    fun zoneIsReadOnEveryCall() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
        val clock: Clock = AppClock()
        assertEquals(ZoneId.of("Pacific/Auckland"), clock.zone)

        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        assertEquals(ZoneId.of("America/Los_Angeles"), clock.zone)
    }

    // SCHED-010 invariant / SCHED-016: dates and times derived from the clock follow the new zone.
    @Test
    fun derivedLocalDateTimeFollowsTheNewZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")) // UTC+14
        val clock: Clock = AppClock()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Pago_Pago")) // UTC-11

        val instant = clock.instant()
        val local = LocalDateTime.now(clock)
        val expected = LocalDateTime.ofInstant(instant, ZoneId.of("Pacific/Pago_Pago"))
        // Read one after the other, so allow a little time to pass between them.
        val drift = java.time.Duration.between(expected, local)
        assertTrue("local time $local is not in Pago Pago (expected about $expected)", !drift.isNegative && drift.seconds < 5)
        // The two zones are 25 hours apart, so the date always differs between them.
        assertEquals(local.toLocalDate(), LocalDate.ofInstant(instant, clock.zone))
        assertTrue(LocalDate.now(clock) != LocalDate.ofInstant(clock.instant(), ZoneId.of("Pacific/Kiritimati")))
    }

    // SCHED-016: the instant is the system time.
    @Test
    fun instantIsTheSystemTime() {
        val clock: Clock = AppClock()
        val before = Clock.systemUTC().millis()
        val read = clock.instant().toEpochMilli()
        val readMillis = clock.millis()
        val after = Clock.systemUTC().millis()
        assertTrue(read in before..after)
        assertTrue(readMillis in read..after)
    }

    // SCHED-016: withZone gives a clock fixed to that zone, still on the system time.
    @Test
    fun withZoneFixesTheZone() {
        val clock = AppClock().withZone(ZoneOffset.UTC)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
        assertEquals(ZoneOffset.UTC, clock.zone)
        val before = Clock.systemUTC().millis()
        assertTrue(clock.instant().toEpochMilli() >= before)
    }
}
