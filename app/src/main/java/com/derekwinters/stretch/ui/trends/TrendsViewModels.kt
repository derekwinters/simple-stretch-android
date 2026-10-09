@file:OptIn(ExperimentalCoroutinesApi::class)

package com.derekwinters.stretch.ui.trends

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.trends.CompletionRef
import com.derekwinters.stretch.trends.DayScore
import com.derekwinters.stretch.trends.GoalPeriod
import com.derekwinters.stretch.trends.IntradayStep
import com.derekwinters.stretch.trends.Period
import com.derekwinters.stretch.trends.PeriodKind
import com.derekwinters.stretch.trends.PeriodSummary
import com.derekwinters.stretch.trends.TrendMath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

/** Everything the trends screen draws (TREND-004..006). */
data class TrendsState(
    val period: Period,
    val today: LocalDate,
    val days: List<DayScore> = emptyList(),
    val summary: PeriodSummary = PeriodSummary(null, 0, 0),
    val streak: Int = 0,
    /** First day any goal was in force, or null when there has never been a goal. */
    val firstGoalDay: LocalDate? = null,
    val loaded: Boolean = false,
) {
    /** TREND-004: no browsing into periods that are entirely in the future... */
    val canGoNext: Boolean get() = period.end.isBefore(today)

    /** ...or entirely before the first goal. */
    val canGoPrevious: Boolean get() = firstGoalDay != null && period.start.isAfter(firstGoalDay)
}

/** The goal history, completions since the first goal, and skipped days, read together. */
private data class TrendInputs(
    val history: List<GoalPeriod>,
    val countsByDay: Map<LocalDate, Map<Long, Int>>,
    val skipped: Set<LocalDate>,
    val firstGoalDay: LocalDate?,
)

class TrendsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as StretchApp).repository
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now()
    private val period = MutableStateFlow(TrendMath.periodContaining(PeriodKind.WEEK, today))

    private val inputs: Flow<TrendInputs> = repo.goalHistory.flatMapLatest { history ->
        val firstGoalDay = history.minOfOrNull { it.fromEpochDay }?.let { LocalDate.ofEpochDay(it) }
        // Completions before the first goal can never score, so they are not read at all.
        val readFrom = if (firstGoalDay == null || firstGoalDay.isAfter(today)) today else firstGoalDay
        combine(repo.completionsSince(readFrom, zone), repo.skippedDates) { completions, skipped ->
            TrendInputs(history, TrendMath.countsByDay(completions, zone), skipped, firstGoalDay)
        }
    }

    val state: StateFlow<TrendsState> = combine(inputs, period) { input, p ->
        val days = TrendMath.scores(p.start, p.end, input.history, input.countsByDay, input.skipped, today)
        // TREND-006: the streak runs back through all history, not just the shown period.
        val streakFrom = input.firstGoalDay?.takeIf { !it.isAfter(today) }
        val streak = if (streakFrom == null) {
            0
        } else {
            TrendMath.currentStreak(
                TrendMath.scores(streakFrom, today, input.history, input.countsByDay, input.skipped, today),
                today,
            )
        }
        TrendsState(
            period = p,
            today = today,
            days = days,
            summary = TrendMath.summary(days),
            streak = streak,
            firstGoalDay = input.firstGoalDay,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsState(period.value, today))

    /** TREND-004: switching between week and month keeps the period around the shown dates. */
    fun setKind(kind: PeriodKind) {
        val current = period.value
        if (current.kind == kind) return
        val anchor = if (today in current) today else current.start
        period.value = TrendMath.periodContaining(kind, anchor)
    }

    fun previous() {
        period.value = TrendMath.shift(period.value, -1)
    }

    fun next() {
        period.value = TrendMath.shift(period.value, 1)
    }
}

/** One goal's row on the day detail screen (TREND-007). */
data class DayGoalRow(val stretchName: String, val target: Int, val done: Int) {
    val met: Boolean get() = done >= target
}

/** One completion on the day detail screen (TREND-007). */
data class DayCompletionRow(val stretchName: String, val atMillis: Long)

data class DayDetailState(
    val date: LocalDate,
    val score: DayScore? = null,
    val goals: List<DayGoalRow> = emptyList(),
    val steps: List<IntradayStep> = emptyList(),
    val completions: List<DayCompletionRow> = emptyList(),
    val loaded: Boolean = false,
)

class DayDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repo = (application as StretchApp).repository
    private val zone: ZoneId = ZoneId.systemDefault()
    val date: LocalDate = LocalDate.ofEpochDay(savedStateHandle.get<Long>(ARG_EPOCH_DAY) ?: LocalDate.now().toEpochDay())

    val state: StateFlow<DayDetailState> = combine(
        repo.goalHistory,
        repo.completionsOn(date, zone),
        repo.skippedDates,
        repo.stretches,
    ) { history, completions, skipped, stretches ->
        val names = stretches.associate { it.id to it.name }
        val counts = completions.groupingBy(CompletionRef::stretchId).eachCount()
        val score = TrendMath.dailyScore(date, history, counts, date in skipped, LocalDate.now())
        DayDetailState(
            date = date,
            score = score,
            goals = score.goals
                .map { DayGoalRow(names[it.stretchId] ?: "Deleted stretch", it.target, it.done) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.stretchName }),
            steps = TrendMath.intradaySeries(date, history, completions, zone),
            completions = completions.map { DayCompletionRow(names[it.stretchId] ?: "Deleted stretch", it.atMillis) },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayDetailState(date))

    companion object {
        const val ARG_EPOCH_DAY = "epochDay"
    }
}
