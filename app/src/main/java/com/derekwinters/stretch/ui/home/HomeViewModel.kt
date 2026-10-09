@file:OptIn(ExperimentalCoroutinesApi::class)

package com.derekwinters.stretch.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.data.ScheduleWithReminders
import com.derekwinters.stretch.data.days
import com.derekwinters.stretch.data.isRepeating
import com.derekwinters.stretch.data.repeatRule
import com.derekwinters.stretch.data.time
import com.derekwinters.stretch.goals.GoalMath
import com.derekwinters.stretch.goals.GoalProgress
import com.derekwinters.stretch.goals.GoalRef
import com.derekwinters.stretch.goals.StretchRef
import com.derekwinters.stretch.scheduling.RepeatRule
import com.derekwinters.stretch.scheduling.RepeatingSlots
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One row of HOME-002. A set reminder time has [stretchNames]; a repeating schedule has a
 * [repeatRule] and shows its next slot today (or its last, once all have passed).
 */
data class TodayReminder(
    val key: String,
    val time: LocalTime,
    val scheduleName: String,
    val stretchNames: List<String>,
    val isPast: Boolean,
    val repeatRule: RepeatRule? = null,
    val slotsLeft: Int = 0,
)

data class HomeState(
    val today: LocalDate = LocalDate.now(),
    val todaySkipped: Boolean = false,
    val todayReminders: List<TodayReminder> = emptyList(),
    val schedules: List<ScheduleWithReminders> = emptyList(),
    val upcomingSkipCount: Int = 0,
    val goals: List<GoalProgress> = emptyList(),
    /** HOME-008: the skip card was closed today. */
    val skipCardClosedToday: Boolean = false,
    val loaded: Boolean = false,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as StretchApp).repository
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** HOME-008: epoch day on which the skip card was closed, or null. Per device only. */
    private val skipCardClosedDay = MutableStateFlow(readClosedDay())

    /** Emits the current time every 30 seconds so "past" markers and the date stay fresh. */
    private fun clock(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }

    /** HOME-006: today's goal progress; re-queried when the date changes. */
    private val goalProgress: Flow<List<GoalProgress>> = clock()
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
        clock(),
        goalProgress,
        skipCardClosedDay,
    ) { schedules, skipped, now, goals, closedDay ->
        val today = now.toLocalDate()
        val nowTime = now.toLocalTime()
        val todays = schedules
            .filter { it.schedule.enabled && today.dayOfWeek in it.schedule.days }
            .flatMap { s ->
                if (s.schedule.isRepeating) {
                    // HOME-002: one row per repeating schedule, at its next slot today.
                    val rule = s.schedule.repeatRule
                    val slots = RepeatingSlots.forDay(rule)
                    if (slots.isEmpty()) return@flatMap emptyList<TodayReminder>()
                    val upcoming = slots.filter { it.isAfter(nowTime) }
                    listOf(
                        TodayReminder(
                            key = "repeat-${s.schedule.id}",
                            time = upcoming.firstOrNull() ?: slots.last(),
                            scheduleName = s.schedule.name,
                            stretchNames = emptyList(),
                            isPast = upcoming.isEmpty(),
                            repeatRule = rule,
                            slotsLeft = upcoming.size,
                        ),
                    )
                } else {
                    s.reminders.map { r ->
                        TodayReminder(
                            key = "reminder-${r.reminder.id}",
                            time = r.reminder.time,
                            scheduleName = s.schedule.name,
                            stretchNames = r.stretches.map { it.name },
                            isPast = !r.reminder.time.isAfter(nowTime),
                        )
                    }
                }
            }
            .sortedBy { it.time }
        HomeState(
            today = today,
            todaySkipped = today in skipped,
            todayReminders = todays,
            schedules = schedules,
            upcomingSkipCount = skipped.count { it.isAfter(today) },
            goals = goals,
            skipCardClosedToday = closedDay == today.toEpochDay(),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setTodaySkipped(skip: Boolean) {
        viewModelScope.launch {
            val today = LocalDate.now()
            if (skip) repo.skipDate(today) else repo.unskipDate(today)
        }
    }

    fun setScheduleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repo.setScheduleEnabled(id, enabled) }
    }

    /** HOME-008: hide the skip card until tomorrow. */
    fun closeSkipCard() {
        val day = LocalDate.now().toEpochDay()
        prefs.edit().putLong(KEY_SKIP_CARD_CLOSED_DAY, day).apply()
        skipCardClosedDay.value = day
    }

    private fun readClosedDay(): Long? =
        if (prefs.contains(KEY_SKIP_CARD_CLOSED_DAY)) prefs.getLong(KEY_SKIP_CARD_CLOSED_DAY, 0L) else null

    private companion object {
        const val PREFS = "home"
        const val KEY_SKIP_CARD_CLOSED_DAY = "skip_card_closed_epoch_day"
    }
}
