@file:OptIn(ExperimentalCoroutinesApi::class)

package com.derekwinters.stretch.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.data.ScheduleWithReminders
import com.derekwinters.stretch.goals.GoalMath
import com.derekwinters.stretch.goals.GoalProgress
import com.derekwinters.stretch.goals.GoalRef
import com.derekwinters.stretch.goals.StretchRef
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
    val schedules: List<ScheduleWithReminders> = emptyList(),
    val goals: List<GoalProgress> = emptyList(),
    val loaded: Boolean = false,
)

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

    /** HOME-006: today's goal progress; re-queried when the date changes. */
    private val goalProgress: Flow<List<GoalProgress>> = ticks()
        .map { it.toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { date ->
            combine(repo.stretches, repo.goals, repo.countsOn(date)) { stretches, goals, counts ->
                GoalMath.progress(
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
        goalProgress,
    ) { schedules, skipped, now, goals ->
        val today = now.toLocalDate()
        val todays = TodayReminders.of(schedules, now)
        HomeState(
            today = today,
            todaySkipped = today in skipped,
            todayReminders = todays,
            schedules = schedules,
            goals = goals,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(today = LocalDate.now(clock)))

    /** SKIP-001 / HOME-002 / HOME-004: the Today row button and the overflow item both call this. */
    fun setTodaySkipped(skip: Boolean) {
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            if (skip) repo.skipDate(today) else repo.unskipDate(today)
        }
    }

    fun setScheduleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repo.setScheduleEnabled(id, enabled) }
    }
}
