package com.derekwinters.stretch.trends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Requirement IDs refer to docs/spec/trends.md. */
class TrendMathTest {
    private val zone = ZoneId.of("America/Chicago")
    private val today = LocalDate.of(2026, 10, 9) // a Friday
    private fun day(d: LocalDate) = d.toEpochDay()

    // Hamstring (1) x3 and calf (2) x2, both since long ago.
    private val history = listOf(
        GoalPeriod(1, 3, day(LocalDate.of(2026, 1, 1)), null),
        GoalPeriod(2, 2, day(LocalDate.of(2026, 1, 1)), null),
    )

    private fun at(date: LocalDate, h: Int, m: Int, stretch: Long) =
        CompletionRef(stretch, LocalDateTime.of(date, LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli())

    // TREND-002: credited completions over targets.
    @Test
    fun scoreIsCreditedOverTarget() {
        val s = TrendMath.dailyScore(today, history, mapOf(1L to 2, 2L to 1), skipped = false, today = today)
        assertEquals(DayStatus.SCORED, s.status)
        assertEquals(3.0 / 5.0, s.score!!, 1e-9)
        assertFalse(s.allMet)
    }

    // TREND-002 invariant: extra completions never lift a day above 100% or make up for another goal.
    @Test
    fun extraCompletionsAreCapped() {
        val s = TrendMath.dailyScore(today, history, mapOf(1L to 9, 2L to 0), skipped = false, today = today)
        assertEquals(3.0 / 5.0, s.score!!, 1e-9)
        val full = TrendMath.dailyScore(today, history, mapOf(1L to 9, 2L to 4, 3L to 7), skipped = false, today = today)
        assertEquals(1.0, full.score!!, 1e-9)
        assertTrue(full.allMet)
    }

    // TREND-003 invariant: gaps are null, never 0%.
    @Test
    fun gapsHaveNoScore() {
        val skipped = TrendMath.dailyScore(today, history, mapOf(1L to 3, 2L to 2), skipped = true, today = today)
        assertEquals(DayStatus.SKIPPED, skipped.status)
        assertNull(skipped.score)
        assertFalse(skipped.allMet)

        val before = TrendMath.dailyScore(LocalDate.of(2025, 12, 31), history, emptyMap(), skipped = false, today = today)
        assertEquals(DayStatus.NO_GOALS, before.status)
        assertNull(before.score)

        val future = TrendMath.dailyScore(today.plusDays(1), history, emptyMap(), skipped = false, today = today)
        assertEquals(DayStatus.FUTURE, future.status)
        assertNull(future.score)

        val zero = TrendMath.dailyScore(today, history, emptyMap(), skipped = false, today = today)
        assertEquals(0.0, zero.score!!, 1e-9)
    }

    // TREND-001: past days are judged by the goals in force that day.
    @Test
    fun goalHistoryDecidesTargets() {
        val changeDay = LocalDate.of(2026, 10, 5)
        val h = listOf(
            GoalPeriod(1, 3, day(LocalDate.of(2026, 9, 1)), day(changeDay)),
            GoalPeriod(1, 1, day(changeDay), null),
            GoalPeriod(2, 2, day(LocalDate.of(2026, 9, 1)), day(LocalDate.of(2026, 10, 7))), // removed on the 7th
        )
        assertEquals(mapOf(1L to 3, 2L to 2), TrendMath.goalsInForce(h, changeDay.minusDays(1)))
        assertEquals(mapOf(1L to 1, 2L to 2), TrendMath.goalsInForce(h, changeDay))
        assertEquals(mapOf(1L to 1), TrendMath.goalsInForce(h, LocalDate.of(2026, 10, 7)))
        assertTrue(TrendMath.goalsInForce(h, LocalDate.of(2026, 8, 31)).isEmpty())

        val before = TrendMath.dailyScore(changeDay.minusDays(1), h, mapOf(1L to 1, 2L to 2), false, today)
        assertEquals(3.0 / 5.0, before.score!!, 1e-9)
        val after = TrendMath.dailyScore(changeDay, h, mapOf(1L to 1, 2L to 2), false, today)
        assertEquals(1.0, after.score!!, 1e-9)
    }

    // GOAL-003 / TREND-002: completions are bucketed by local day.
    @Test
    fun countsByLocalDay() {
        val d = LocalDate.of(2026, 10, 8)
        val counts = TrendMath.countsByDay(
            listOf(at(d, 23, 59, 1), at(d.plusDays(1), 0, 0, 1), at(d.plusDays(1), 0, 1, 2), at(d, 8, 0, 1)),
            zone,
        )
        assertEquals(mapOf(1L to 2), counts[d])
        assertEquals(mapOf(1L to 1, 2L to 1), counts[d.plusDays(1)])
    }

    // TREND-004: weeks start on Monday.
    @Test
    fun weeksStartMonday() {
        val w = TrendMath.periodContaining(PeriodKind.WEEK, today)
        assertEquals(LocalDate.of(2026, 10, 5), w.start)
        assertEquals(LocalDate.of(2026, 10, 11), w.end)
        assertEquals(7, w.days.size)
        // Sunday belongs to the week that started the Monday before it.
        assertEquals(LocalDate.of(2026, 10, 5), TrendMath.periodContaining(PeriodKind.WEEK, LocalDate.of(2026, 10, 11)).start)
        assertEquals(LocalDate.of(2026, 10, 12), TrendMath.periodContaining(PeriodKind.WEEK, LocalDate.of(2026, 10, 12)).start)
        // A week across a month and a year boundary.
        val nye = TrendMath.periodContaining(PeriodKind.WEEK, LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), nye.start)
        assertEquals(LocalDate.of(2027, 1, 3), nye.end)
        assertEquals(LocalDate.of(2026, 9, 28), TrendMath.shift(w, -1).start)
        assertEquals(LocalDate.of(2026, 10, 12), TrendMath.shift(w, 1).start)
    }

    // TREND-004: months run from the 1st to their last day, including February in leap years.
    @Test
    fun monthBoundaries() {
        val m = TrendMath.periodContaining(PeriodKind.MONTH, today)
        assertEquals(LocalDate.of(2026, 10, 1), m.start)
        assertEquals(LocalDate.of(2026, 10, 31), m.end)
        assertEquals(31, m.days.size)
        val feb = TrendMath.periodContaining(PeriodKind.MONTH, LocalDate.of(2028, 2, 15))
        assertEquals(LocalDate.of(2028, 2, 29), feb.end)
        val prev = TrendMath.shift(TrendMath.periodContaining(PeriodKind.MONTH, LocalDate.of(2026, 3, 31)), -1)
        assertEquals(LocalDate.of(2026, 2, 1), prev.start)
        assertEquals(LocalDate.of(2026, 2, 28), prev.end)
        val next = TrendMath.shift(TrendMath.periodContaining(PeriodKind.MONTH, LocalDate.of(2026, 12, 5)), 1)
        assertEquals(LocalDate.of(2027, 1, 1), next.start)
        assertTrue(LocalDate.of(2027, 1, 31) in next)
        assertFalse(LocalDate.of(2027, 2, 1) in next)
    }

    // TREND-005: the average leaves gaps out; all-met days are counted.
    @Test
    fun summaryIgnoresGaps() {
        val week = TrendMath.periodContaining(PeriodKind.WEEK, today)
        val counts = mapOf(
            LocalDate.of(2026, 10, 5) to mapOf(1L to 3, 2L to 2), // 100%
            LocalDate.of(2026, 10, 6) to mapOf(1L to 1), // 20%
            LocalDate.of(2026, 10, 7) to mapOf(1L to 3, 2L to 2), // skipped
            // the 8th: nothing logged, 0%
            LocalDate.of(2026, 10, 9) to mapOf(1L to 3, 2L to 2), // 100% (today)
        )
        val scores = TrendMath.scores(week.start, week.end, history, counts, setOf(LocalDate.of(2026, 10, 7)), today)
        val summary = TrendMath.summary(scores)
        assertEquals(4, summary.countedDays)
        assertEquals((1.0 + 0.2 + 0.0 + 1.0) / 4, summary.average!!, 1e-9)
        assertEquals(2, summary.allMetDays)
        assertNull(TrendMath.summary(TrendMath.scores(week.start, week.end, emptyList(), counts, emptySet(), today)).average)
    }

    private fun scoresEndingToday(vararg days: Pair<LocalDate, Map<Long, Int>>, skipped: Set<LocalDate> = emptySet()) =
        TrendMath.scores(LocalDate.of(2026, 9, 25), today, history, days.toMap(), skipped, today)

    private val met = mapOf(1L to 3, 2L to 2)

    // TREND-006: a streak can end yesterday; an unfinished today doesn't break it.
    @Test
    fun streakEndingYesterday() {
        val s = scoresEndingToday(
            LocalDate.of(2026, 10, 6) to met,
            LocalDate.of(2026, 10, 7) to met,
            LocalDate.of(2026, 10, 8) to met,
            today to mapOf(1L to 1),
        )
        assertEquals(3, TrendMath.currentStreak(s, today))
    }

    // TREND-006: a met today extends the streak.
    @Test
    fun streakIncludingToday() {
        val s = scoresEndingToday(LocalDate.of(2026, 10, 8) to met, today to met)
        assertEquals(2, TrendMath.currentStreak(s, today))
    }

    // TREND-006: a missed day breaks it; skipped and goal-less days are passed over.
    @Test
    fun streakBreaksOnMissNotOnGaps() {
        val s = scoresEndingToday(
            LocalDate.of(2026, 10, 4) to met,
            LocalDate.of(2026, 10, 5) to mapOf(1L to 3), // missed calf
            LocalDate.of(2026, 10, 6) to met,
            // the 7th skipped
            LocalDate.of(2026, 10, 8) to met,
            skipped = setOf(LocalDate.of(2026, 10, 7)),
        )
        assertEquals(2, TrendMath.currentStreak(s, today))
        assertEquals(0, TrendMath.currentStreak(scoresEndingToday(), today))
        // Before any goal existed, the run simply ends.
        val h = listOf(GoalPeriod(1, 1, day(LocalDate.of(2026, 10, 7)), null))
        val s2 = TrendMath.scores(LocalDate.of(2026, 10, 1), today, h, mapOf(
            LocalDate.of(2026, 10, 7) to mapOf(1L to 1),
            LocalDate.of(2026, 10, 8) to mapOf(1L to 2),
        ), emptySet(), today)
        assertEquals(2, TrendMath.currentStreak(s2, today))
    }

    // TREND-008: steps at each credited completion; same-moment saves are one step; extras are flat.
    @Test
    fun intradaySeries() {
        val c = listOf(
            at(today, 9, 0, 1),
            at(today, 9, 0, 2), // same session save as above
            at(today, 12, 30, 3), // no goal for stretch 3
            at(today, 13, 0, 1),
            at(today, 15, 0, 1),
            at(today, 16, 0, 1), // beyond target: no step
            at(today, 17, 45, 2),
            at(today.minusDays(1), 10, 0, 1), // another day
        )
        val steps = TrendMath.intradaySeries(today, history, c, zone)
        assertEquals(
            listOf(
                IntradayStep(0, 0.0),
                IntradayStep(9 * 3600, 0.4),
                IntradayStep(13 * 3600, 0.6),
                IntradayStep(15 * 3600, 0.8),
                IntradayStep(17 * 3600 + 45 * 60, 1.0),
            ).map { it.secondOfDay to it.score },
            steps.map { it.secondOfDay to Math.round(it.score * 1000) / 1000.0 },
        )
        assertTrue(TrendMath.intradaySeries(today, emptyList(), c, zone).isEmpty())
        assertEquals(listOf(IntradayStep(0, 0.0)), TrendMath.intradaySeries(today, history, emptyList(), zone))
    }

    // TREND-008: on the night clocks go back, time of day never runs backwards.
    @Test
    fun intradaySeriesAcrossFallBack() {
        val fallBack = LocalDate.of(2026, 11, 1) // America/Chicago, 02:00 -> 01:00
        val first = LocalDateTime.of(fallBack, LocalTime.of(1, 30)).atZone(zone).withEarlierOffsetAtOverlap()
        val second = first.plusHours(1).withLaterOffsetAtOverlap().minusMinutes(20) // 01:10, after first in time
        val c = listOf(
            CompletionRef(1, first.toInstant().toEpochMilli()),
            CompletionRef(2, second.toInstant().toEpochMilli()),
        )
        val steps = TrendMath.intradaySeries(fallBack, history, c, zone)
        assertTrue(steps.zipWithNext().all { (a, b) -> b.secondOfDay >= a.secondOfDay })
        // The later 01:10 is held at 01:30, so both completions land on one step.
        assertEquals(listOf(0, 5400), steps.map { it.secondOfDay })
        assertEquals(0.4, steps.last().score, 1e-9)
    }
}
