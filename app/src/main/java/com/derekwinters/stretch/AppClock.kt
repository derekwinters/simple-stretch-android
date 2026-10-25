package com.derekwinters.stretch

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The app's one clock (SCHED-016), created in [StretchApp] and handed to everything that needs
 * the current date, time or zone. This is the only file under `app/src/main` allowed to read the
 * system clock or the default zone; `pr.yml` checks that with a grep.
 *
 * SCHED-010 invariant: [getZone] reads the device's default zone on every call, never one saved
 * when the clock was built, so a time-zone change is seen at once by a clock built at process
 * start. Tests and screenshot runs use `Clock.fixed(instant, zone)` instead.
 */
class AppClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun instant(): Instant = Instant.ofEpochMilli(System.currentTimeMillis())

    override fun millis(): Long = System.currentTimeMillis()

    /** A clock on the system time fixed to [zone]: the caller asked for that zone, not the default. */
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)

    override fun equals(other: Any?): Boolean = other is AppClock

    override fun hashCode(): Int = AppClock::class.hashCode()

    override fun toString(): String = "AppClock[default zone]"
}
