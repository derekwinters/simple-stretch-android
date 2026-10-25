package com.derekwinters.stretch.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Requirement IDs refer to docs/spec/sessions.md, docs/spec/notifications.md and docs/spec/screens.md. */
class SessionRouteTest {

    // SESS-001 / NOTIF-002 / HOME-009: a non-ASCII schedule name is percent-encoded byte by byte
    // as UTF-8, with uppercase hex, as android.net.Uri.encode does.
    @Test
    fun nonAsciiScheduleNameIsUtf8PercentEncoded() {
        assertEquals(
            "session?ids=1&schedule=Caf%C3%A9%20%F0%9F%A7%98&at=0",
            SessionRoute.of(listOf(1L), "Café 🧘", 0L),
        )
    }

    // SESS-001 / NOTIF-002 / HOME-009: letters, digits and the unreserved set _-!.~'()* pass
    // through unchanged, as android.net.Uri.encode leaves them.
    @Test
    fun unreservedCharactersPassThrough() {
        assertEquals(
            "session?ids=1&schedule=aZ09_-!.~'()*&at=0",
            SessionRoute.of(listOf(1L), "aZ09_-!.~'()*", 0L),
        )
    }

    // SESS-001 / NOTIF-002 / HOME-009: without a schedule name the reminder origin is incomplete,
    // so the session opens with no named list.
    @Test
    fun missingScheduleNameOpensWithNoNamedList() {
        assertEquals("session", SessionRoute.of(listOf(3L, 1L), null, 1_000L))
    }

    // SESS-001 / NOTIF-002 / HOME-009: without a scheduled time (the -1 an intent extra defaults
    // to), the session opens with no named list.
    @Test
    fun missingScheduledTimeOpensWithNoNamedList() {
        assertEquals("session", SessionRoute.of(listOf(3L, 1L), "Morning", -1L))
    }
}
