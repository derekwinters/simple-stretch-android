package com.derekwinters.stretch.store

import android.content.Context
import com.derekwinters.stretch.data.DefaultStretches
import com.derekwinters.stretch.data.Reminder
import com.derekwinters.stretch.data.ReminderWithStretches
import com.derekwinters.stretch.data.Schedule
import com.derekwinters.stretch.data.ScheduleMode
import com.derekwinters.stretch.data.ScheduleWithReminders
import com.derekwinters.stretch.data.Stretch
import com.derekwinters.stretch.data.time
import com.derekwinters.stretch.goals.GoalMath
import com.derekwinters.stretch.goals.GoalRef
import com.derekwinters.stretch.goals.SessionHeadings
import com.derekwinters.stretch.goals.SessionLayout
import com.derekwinters.stretch.goals.StretchRef
import com.derekwinters.stretch.scheduling.DaysOfWeek
import com.derekwinters.stretch.trends.CompletionRef
import com.derekwinters.stretch.trends.GoalPeriod
import com.derekwinters.stretch.trends.PeriodKind
import com.derekwinters.stretch.trends.TrendMath
import com.derekwinters.stretch.ui.common.Formatting
import com.derekwinters.stretch.ui.goals.GoalsState
import com.derekwinters.stretch.ui.home.HomeState
import com.derekwinters.stretch.ui.home.TodayReminders
import com.derekwinters.stretch.ui.schedule.ScheduleEditForm
import com.derekwinters.stretch.ui.trends.TrendsState
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * STORE-SHOT-006: the showcase data behind every store shot, built straight into each screen's
 * state. No database, no `StretchApp`, no randomness: the time is a fixed clock (SCHED-016) and
 * every count is written out below, so the same commit always draws the same pixels. The state
 * is derived through the app's own pure functions (TodayReminders, GoalMath, TrendMath), as the
 * ViewModels derive it.
 */
object ShowcaseFixture {
    /** STORE-SHOT-006: a weekday (Friday 16 October 2026) at 9:40 AM in New York. */
    val zone: ZoneId = ZoneId.of("America/New_York")
    val now: LocalDateTime = LocalDateTime.of(2026, 10, 16, 9, 40)
    val clock: Clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    val today: LocalDate = now.toLocalDate()

    /** LIB-002: the seeded library, with the ids a fresh install gives it (1 to 10, in seed order). */
    val stretches: List<Stretch> = DefaultStretches.ALL.mapIndexed { i, s -> s.copy(id = i + 1L) }

    private fun id(name: String): Long = stretches.single { it.name == name }.id
    private val neckRolls = id("Neck rolls")
    private val shoulderShrugs = id("Shoulder shrugs")
    private val hamstring = id("Hamstring stretch")
    private val hipFlexor = id("Hip flexor stretch")
    private val wrist = id("Wrist stretch")
    private val upperBack = id("Upper back stretch")
    private val calf = id("Calf stretch")

    /** Three goals (GOAL-001), in force since well before the history starts. */
    private val goalTargets: Map<Long, Int> = mapOf(neckRolls to 2, hamstring to 3, calf to 3)
    private val goals: List<GoalRef> = goalTargets.map { (stretchId, count) -> GoalRef(stretchId, count) }
    private val goalHistory: List<GoalPeriod> = goalTargets.map { (stretchId, count) ->
        GoalPeriod(stretchId, count, fromEpochDay = today.minusDays(40).toEpochDay(), toEpochDay = null)
    }

    /** Today so far: every goal partway done (GOAL-003), plus one stretch without a goal. */
    private val countsToday: Map<Long, Int> = mapOf(neckRolls to 1, hamstring to 2, calf to 1, shoulderShrugs to 1)

    /**
     * Counts per goal stretch for the 30 days before today, most recent first: the last 12 days
     * met every goal (the streak, TREND-006), the day before fell short, and the rest vary.
     */
    private val pastCounts: List<Triple<Int, Int, Int>> = List(12) { Triple(2, 3, 3) } + listOf(
        Triple(1, 3, 2), Triple(2, 3, 3), Triple(2, 2, 3), Triple(2, 3, 3), Triple(0, 1, 1),
        Triple(2, 3, 3), Triple(1, 2, 2), Triple(2, 3, 3), Triple(2, 3, 1), Triple(2, 3, 3),
        Triple(1, 1, 2), Triple(2, 3, 3), Triple(2, 2, 2), Triple(0, 3, 3), Triple(2, 3, 3),
        Triple(1, 2, 3), Triple(2, 3, 3), Triple(2, 1, 0),
    )

    /** The completion log for those 30 days and today, at fixed times through each day. */
    private val completions: List<CompletionRef> = buildList {
        fun log(date: LocalDate, stretchId: Long, times: Int) {
            repeat(times) { i ->
                val at = date.atTime(LocalTime.of(9, 5).plusHours(2L * i)).atZone(zone)
                add(CompletionRef(stretchId, at.toInstant().toEpochMilli()))
            }
        }
        pastCounts.forEachIndexed { i, (neck, ham, calves) ->
            val date = today.minusDays(i + 1L)
            log(date, neckRolls, neck)
            log(date, hamstring, ham)
            log(date, calf, calves)
        }
        // Today's are all before 9:40, so none is in the future of the fixed clock.
        countsToday.forEach { (stretchId, count) ->
            repeat(count) { i ->
                val at = today.atTime(LocalTime.of(8, 30).plusMinutes(10L * i)).atZone(zone)
                add(CompletionRef(stretchId, at.toInstant().toEpochMilli()))
            }
        }
    }

    init {
        check(pastCounts.size == 30) { "30 days of history, not ${pastCounts.size}" }
    }

    private val stretchRefs: List<StretchRef> = stretches.map { StretchRef(it.id, it.name) }

    private fun stretch(id: Long): Stretch = stretches.single { it.id == id }

    private fun setTime(id: Long, scheduleId: Long, time: LocalTime, vararg stretchIds: Long) =
        ReminderWithStretches(
            reminder = Reminder(id = id, scheduleId = scheduleId, minuteOfDay = time.hour * 60 + time.minute),
            stretches = stretchIds.map(::stretch),
        )

    /** A set-times schedule (SCHED-002) with its stretches per time. */
    private val deskBreaks = ScheduleWithReminders(
        schedule = Schedule(id = 1, name = "Desk breaks", daysMask = DaysOfWeek.toMask(DaysOfWeek.WEEKDAYS)),
        reminders = listOf(
            setTime(1, 1, LocalTime.of(9, 0), neckRolls, shoulderShrugs, upperBack),
            setTime(2, 1, LocalTime.of(12, 30), hamstring, calf),
            setTime(3, 1, LocalTime.of(15, 30), hipFlexor, wrist),
        ),
    )

    /** A repeating schedule (SCHED-011): every hour at :50 from 8:00 AM to 5:00 PM. */
    private val workday = ScheduleWithReminders(
        schedule = Schedule(
            id = 2,
            name = "Workday",
            daysMask = DaysOfWeek.toMask(DaysOfWeek.WEEKDAYS),
            mode = ScheduleMode.REPEATING,
            windowStartMinute = 8 * 60,
            windowEndMinute = 17 * 60,
            intervalMinutes = 60,
            minutePastHour = 50,
        ),
        reminders = emptyList(),
    )

    private val schedules: List<ScheduleWithReminders> = listOf(deskBreaks, workday)

    /** STORE-SHOT-001: home, as HomeViewModel derives it (HOME-002, HOME-006). */
    fun home(): HomeState = HomeState(
        today = today,
        todaySkipped = false,
        todayReminders = TodayReminders.of(schedules, now),
        schedules = schedules,
        goals = GoalMath.progress(stretchRefs, goals, countsToday),
        loaded = true,
    )

    /** STORE-SHOT-002: the session opened from the 9:00 AM "Desk breaks" reminder (SESS-001). */
    private val sessionReminder = deskBreaks.reminders.first()

    fun sessionLayout(): SessionLayout = GoalMath.sessionLayout(
        stretchRefs,
        goals,
        countsToday,
        reminderStretchIds = sessionReminder.stretches.map { it.id },
    )

    /** SESS-002: the top section's heading, formatted as SessionViewModel formats it. */
    fun sessionHeading(context: Context): String = SessionHeadings.fromReminder(
        Formatting.time(context, sessionReminder.reminder.time),
        deskBreaks.schedule.name,
    )

    /** The user has ticked two of the reminder's three stretches, not yet saved (SESS-002, SESS-003). */
    val sessionChecked: Set<Long> = setOf(neckRolls, shoulderShrugs)

    /** STORE-SHOT-003: the goals screen (GOAL-002, GOAL-003). */
    fun goals(): GoalsState = GoalsState(GoalMath.goalRows(stretchRefs, goals, countsToday), loaded = true)

    /** STORE-SHOT-004: the editor opened on the repeating schedule (SCHED-011, SCHED-012). */
    fun scheduleForm(): ScheduleEditForm = ScheduleEditForm().apply { load(workday) }

    /** STORE-SHOT-005: this week's trends, as TrendsViewModel derives them (TREND-004 to TREND-006). */
    fun trends(): TrendsState {
        val countsByDay = TrendMath.countsByDay(completions, zone)
        val firstGoalDay = goalHistory.minOf { it.fromEpochDay }.let(LocalDate::ofEpochDay)
        val period = TrendMath.periodContaining(PeriodKind.WEEK, today)
        val days = TrendMath.scores(period.start, period.end, goalHistory, countsByDay, emptySet(), today)
        val streak = TrendMath.currentStreak(
            TrendMath.scores(firstGoalDay, today, goalHistory, countsByDay, emptySet(), today),
            today,
        )
        return TrendsState(
            period = period,
            today = today,
            days = days,
            summary = TrendMath.summary(days),
            streak = streak,
            firstGoalDay = firstGoalDay,
            loaded = true,
        )
    }
}
