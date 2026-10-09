package com.derekwinters.stretch.trends

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Pure (Android-free) trend maths, unit tested on the JVM (docs/spec/trends.md). Like goal
 * progress (GOAL-004), everything here is derived from the goal history and the completion log;
 * nothing is stored.
 */

/**
 * One row of goal history (GOAL-008): [timesPerDay] for [stretchId] is in force from
 * [fromEpochDay] (inclusive) to [toEpochDay] (exclusive), or open-ended when that is null.
 */
data class GoalPeriod(
    val stretchId: Long,
    val timesPerDay: Int,
    val fromEpochDay: Long,
    val toEpochDay: Long?,
) {
    fun inForceOn(epochDay: Long): Boolean =
        epochDay >= fromEpochDay && (toEpochDay == null || epochDay < toEpochDay)
}

/** One logged completion, without Room types. */
data class CompletionRef(val stretchId: Long, val atMillis: Long)

/** Why a day has no score (TREND-003). */
enum class DayStatus {
    /** Scored: at least one goal in force, not skipped, not in the future. */
    SCORED,

    /** No goal was in force that day. */
    NO_GOALS,

    /** The day was skipped (SKIP-001, SKIP-002). */
    SKIPPED,

    /** After today. */
    FUTURE,
}

/** One goal on one day: [done] is the raw count, [credited] is capped at [target] (TREND-002). */
data class DayGoal(val stretchId: Long, val target: Int, val done: Int) {
    val credited: Int get() = done.coerceAtMost(target)
    val met: Boolean get() = done >= target
}

/** TREND-002/003: a day's score, or a gap. [score] is null exactly when [status] is not SCORED. */
data class DayScore(
    val date: LocalDate,
    val status: DayStatus,
    val goals: List<DayGoal>,
) {
    val credited: Int get() = goals.sumOf { it.credited }
    val target: Int get() = goals.sumOf { it.target }

    /** 0.0 to 1.0, or null for a gap (never 0 for a gap). */
    val score: Double?
        get() = if (status == DayStatus.SCORED && target > 0) credited.toDouble() / target else null

    /** TREND-005: every goal in force that day was met. False for gaps. */
    val allMet: Boolean get() = status == DayStatus.SCORED && goals.isNotEmpty() && goals.all { it.met }
}

enum class PeriodKind { WEEK, MONTH }

/** A calendar week (Monday to Sunday) or month, both ends inclusive (TREND-004). */
data class Period(val kind: PeriodKind, val start: LocalDate, val end: LocalDate) {
    val days: List<LocalDate>
        get() = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/** TREND-005: the period summary. [average] is null when no day in the period was scored. */
data class PeriodSummary(
    val average: Double?,
    val countedDays: Int,
    val allMetDays: Int,
)

/** TREND-008: one step of the intraday chart: from [secondOfDay] on, the score is [score]. */
data class IntradayStep(val secondOfDay: Int, val score: Double)

object TrendMath {
    /**
     * TREND-001: the goals in force on [date], one per stretch. If history rows ever overlap for
     * a stretch, the one that started latest wins.
     */
    fun goalsInForce(history: List<GoalPeriod>, date: LocalDate): Map<Long, Int> {
        val day = date.toEpochDay()
        return history
            .filter { it.inForceOn(day) }
            .groupBy { it.stretchId }
            .mapValues { (_, rows) -> rows.maxBy { it.fromEpochDay }.timesPerDay.coerceAtLeast(1) }
    }

    /**
     * TREND-002/003: [date]'s score from the goals in force that day and that day's completion
     * counts per stretch. Skipped, goal-less and future days are gaps.
     */
    fun dailyScore(
        date: LocalDate,
        history: List<GoalPeriod>,
        countsThatDay: Map<Long, Int>,
        skipped: Boolean,
        today: LocalDate,
    ): DayScore {
        val targets = goalsInForce(history, date)
        val goals = targets.map { (id, target) -> DayGoal(id, target, countsThatDay[id] ?: 0) }
            .sortedBy { it.stretchId }
        val status = when {
            date.isAfter(today) -> DayStatus.FUTURE
            skipped -> DayStatus.SKIPPED
            goals.isEmpty() -> DayStatus.NO_GOALS
            else -> DayStatus.SCORED
        }
        return DayScore(date, status, goals)
    }

    /** GOAL-003: completion counts per local date, then per stretch id. */
    fun countsByDay(completions: List<CompletionRef>, zone: ZoneId): Map<LocalDate, Map<Long, Int>> =
        completions
            .groupBy { Instant.ofEpochMilli(it.atMillis).atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.groupingBy { it.stretchId }.eachCount() }

    /** Scores for every day in [from]..[to] (inclusive). */
    fun scores(
        from: LocalDate,
        to: LocalDate,
        history: List<GoalPeriod>,
        countsByDay: Map<LocalDate, Map<Long, Int>>,
        skipped: Set<LocalDate>,
        today: LocalDate,
    ): List<DayScore> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .map { d -> dailyScore(d, history, countsByDay[d].orEmpty(), d in skipped, today) }
            .toList()

    /** TREND-004: the week (Monday first) or month containing [date]. */
    fun periodContaining(kind: PeriodKind, date: LocalDate): Period = when (kind) {
        PeriodKind.WEEK -> {
            val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Period(kind, start, start.plusDays(6))
        }
        PeriodKind.MONTH -> Period(kind, date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()))
    }

    /** TREND-004: the period [delta] weeks or months away from [period]. */
    fun shift(period: Period, delta: Long): Period = when (period.kind) {
        PeriodKind.WEEK -> periodContaining(period.kind, period.start.plusWeeks(delta))
        PeriodKind.MONTH -> periodContaining(period.kind, period.start.plusMonths(delta))
    }

    /** TREND-005: average over scored days only; gaps are left out, not counted as 0%. */
    fun summary(days: List<DayScore>): PeriodSummary {
        val scored = days.mapNotNull { it.score }
        return PeriodSummary(
            average = if (scored.isEmpty()) null else scored.average(),
            countedDays = scored.size,
            allMetDays = days.count { it.allMet },
        )
    }

    /**
     * TREND-006: the current run of all-goals-met days. [days] must be consecutive, oldest first,
     * ending at [today]. Today counts when already met but never breaks the streak (the day is
     * not over). Gaps (skipped, goal-less) are passed over: they neither add to nor break it. The
     * first earlier scored day that is not fully met ends the streak.
     */
    fun currentStreak(days: List<DayScore>, today: LocalDate): Int {
        var streak = 0
        for (day in days.asReversed()) {
            if (day.date.isAfter(today)) continue
            when {
                day.allMet -> streak++
                day.date == today -> Unit
                day.status != DayStatus.SCORED -> Unit
                else -> return streak
            }
        }
        return streak
    }

    /**
     * TREND-008: the cumulative score through [date]. Starts at 0 at midnight and steps up at each
     * completion that brings a goal closer to its target; completions beyond a target, or of
     * stretches without a goal that day, never raise it. Completions at the same moment (one
     * session save) make one step. Empty when no goal was in force that day.
     */
    fun intradaySeries(
        date: LocalDate,
        history: List<GoalPeriod>,
        completions: List<CompletionRef>,
        zone: ZoneId,
    ): List<IntradayStep> {
        val targets = goalsInForce(history, date)
        val total = targets.values.sum()
        if (total == 0) return emptyList()
        val steps = mutableListOf(IntradayStep(0, 0.0))
        val counts = mutableMapOf<Long, Int>()
        var credited = 0
        var lastSecond = 0
        for (c in completions.sortedBy { it.atMillis }) {
            val at = Instant.ofEpochMilli(c.atMillis).atZone(zone)
            if (at.toLocalDate() != date) continue
            val target = targets[c.stretchId] ?: continue
            val done = (counts[c.stretchId] ?: 0) + 1
            counts[c.stretchId] = done
            if (done > target) continue
            credited++
            // A repeated hour when clocks go back could move backwards; keep x non-decreasing.
            val second = maxOf(lastSecond, at.toLocalTime().toSecondOfDay())
            lastSecond = second
            val step = IntradayStep(second, credited.toDouble() / total)
            if (steps.last().secondOfDay == second) steps[steps.lastIndex] = step else steps += step
        }
        return steps
    }
}
