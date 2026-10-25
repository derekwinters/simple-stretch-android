package com.derekwinters.stretch.ui.home

import com.derekwinters.stretch.ui.SessionRoute
import java.time.LocalDate
import java.time.ZoneId

/**
 * HOME-009: the session route this Today row opens: the same one its reminder's notification
 * opens (NOTIF-002), built by the same [SessionRoute.of] from the reminder's stretch ids, its
 * schedule name and its time on [date] in [zone]. A repeating schedule's row names no stretches
 * (SCHED-011), so it opens with no named list, as its notification does.
 */
fun TodayReminder.sessionRoute(date: LocalDate, zone: ZoneId): String =
    SessionRoute.of(
        stretchIds = stretchIds,
        scheduleName = scheduleName,
        scheduledAt = date.atTime(time).atZone(zone).toInstant().toEpochMilli(),
    )
