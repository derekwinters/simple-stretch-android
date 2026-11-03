@file:OptIn(ExperimentalCoroutinesApi::class)

package com.derekwinters.stretch.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.goals.GoalRef
import com.derekwinters.stretch.goals.StretchRef
import com.derekwinters.stretch.widget.GoalRing
import com.derekwinters.stretch.widget.RingModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

data class HomeState(
    val today: LocalDate,
    val todaySkipped: Boolean = false,
    val todayReminders: List<TodayReminder> = emptyList(),
    /** HOME-006: the widget's ring for today's current goals (no goals: the dotted ring). */
    val ring: RingModel = GoalRing.model(emptyList()),
    val loaded: Boolean = false,
) {
    /** HOME-002: upcoming reminders first, then today's past ones, greyed. */
    val todayList: TodayList get() = TodayList.of(todayReminders, todaySkipped)
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StretchApp
    private val repo = app.repository

    /**
     * SCHED-016: the app clock; every "now" on this screen is read from it, and a Today row's
     * scheduled time is taken in its zone, as the alarms are (HOME-009).
     */
    val clock: Clock = app.clock

    /**
     * Reads the app clock every 30 seconds so "past" markers and the date stay fresh (HOME-002).
     * The local time is taken in the clock's zone at each tick (SCHED-010 invariant).
     */
    private fun ticks(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now(clock))
            delay(30_000)
        }
    }

    /**
     * HOME-006: the widget's ring for today, built by the widget's own GoalRing.fromLog
     * (WIDGET-009); re-queried when the date changes.
     */
    private val ring: Flow<RingModel> = ticks()
        .map { it.toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { date ->
            combine(repo.stretches, repo.goals, repo.countsOn(date)) { stretches, goals, counts ->
                GoalRing.fromLog(
                    stretches.map { StretchRef(it.id, it.name) },
                    goals.map { GoalRef(it.stretchId, it.timesPerDay) },
                    counts,
                )
            }
        }

    val state: StateFlow<HomeState> = combine(
        repo.schedules,
        repo.skippedDates,
        ticks(),
        ring,
    ) { schedules, skipped, now, todayRing ->
        val today = now.toLocalDate()
        val todays = TodayReminders.of(schedules, now)
        HomeState(
            today = today,
            todaySkipped = today in skipped,
            todayReminders = todays,
            ring = todayRing,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(today = LocalDate.now(clock)))

    /** SKIP-001 / HOME-002: the Today heading's button, the only way to skip today on Home. */
    fun setTodaySkipped(skip: Boolean) {
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            if (skip) repo.skipDate(today) else repo.unskipDate(today)
        }
    }
}
