package com.derekwinters.stretch.goals

import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure (Android-free) goal maths, unit tested on the JVM. Progress is always derived from the
 * completion log (GOAL-004); nothing here stores state.
 */

/** One goal's progress on a day (GOAL-003). */
data class GoalProgress(
    val stretchId: Long,
    val stretchName: String,
    val target: Int,
    val done: Int,
) {
    val met: Boolean get() = done >= target
    val remaining: Int get() = (target - done).coerceAtLeast(0)
    val fraction: Float get() = if (target <= 0) 1f else (done.toFloat() / target).coerceIn(0f, 1f)
}

/** A row on the session screen (SESS-002). [target] is null for a stretch without a goal. */
data class SessionEntry(
    val stretchId: Long,
    val name: String,
    val target: Int?,
    val done: Int,
) {
    val goalUnmet: Boolean get() = target != null && done < target
}

/** Minimal stretch/goal inputs so this file stays free of Room entities. */
data class StretchRef(val id: Long, val name: String)
data class GoalRef(val stretchId: Long, val timesPerDay: Int)

object GoalMath {
    /** GOAL-003: [start of [date], start of the next day) in epoch millis, local to [zone]. */
    fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Long, Long> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    /**
     * GOAL-003: progress for each goal whose stretch exists, given today's completion counts per
     * stretch id. Ordered by stretch name, ignoring case.
     */
    fun progress(
        stretches: List<StretchRef>,
        goals: List<GoalRef>,
        countsToday: Map<Long, Int>,
    ): List<GoalProgress> {
        val names = stretches.associate { it.id to it.name }
        return goals
            .mapNotNull { g ->
                val name = names[g.stretchId] ?: return@mapNotNull null
                GoalProgress(g.stretchId, name, g.timesPerDay.coerceAtLeast(1), countsToday[g.stretchId] ?: 0)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.stretchName })
    }

    /**
     * SESS-002: every stretch; unmet goals first, then everything else; each group by name,
     * ignoring case (ties broken by id so the order is stable).
     */
    fun sessionOrder(
        stretches: List<StretchRef>,
        goals: List<GoalRef>,
        countsToday: Map<Long, Int>,
    ): List<SessionEntry> {
        val targets = goals.associate { it.stretchId to it.timesPerDay.coerceAtLeast(1) }
        val entries = stretches.map { s ->
            SessionEntry(s.id, s.name, targets[s.id], countsToday[s.id] ?: 0)
        }
        val byName = compareBy<SessionEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.stretchId }
        val (unmet, rest) = entries.partition { it.goalUnmet }
        return unmet.sortedWith(byName) + rest.sortedWith(byName)
    }

    /**
     * NOTIF-001: lines for a reminder without stretches. Null when there are no goals (the
     * caller shows the generic message); a single "all done" line when every goal is met.
     */
    fun reminderSummary(progress: List<GoalProgress>): List<String>? {
        if (progress.isEmpty()) return null
        val unmet = progress.filter { !it.met }
        if (unmet.isEmpty()) return listOf(ALL_DONE)
        return unmet.map { "${it.stretchName} ${it.done} of ${it.target}" }
    }

    /**
     * NOTIF-010 / GOAL-005: true when at least one goal exists and every goal is met, i.e. a
     * reminder should not be shown. With no goals this is false, so reminders behave as if goals
     * did not exist.
     */
    fun allGoalsMet(progress: List<GoalProgress>): Boolean =
        progress.isNotEmpty() && progress.all { it.met }

    /** Fallback text only: reminders are not shown at all once every goal is met (NOTIF-010). */
    const val ALL_DONE = "All of today's goals are done. Stretch anyway?"
}
