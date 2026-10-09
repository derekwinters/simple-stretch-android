package com.derekwinters.stretch.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.data.ScheduleWithReminders
import com.derekwinters.stretch.data.days
import com.derekwinters.stretch.data.time
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class TodayReminder(
    val reminderId: Long,
    val time: LocalTime,
    val scheduleName: String,
    val stretchNames: List<String>,
    val isPast: Boolean,
)

data class HomeState(
    val today: LocalDate = LocalDate.now(),
    val todaySkipped: Boolean = false,
    val todayReminders: List<TodayReminder> = emptyList(),
    val schedules: List<ScheduleWithReminders> = emptyList(),
    val upcomingSkipCount: Int = 0,
    val loaded: Boolean = false,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as StretchApp).repository

    /** Emits the current time every 30 seconds so "past" markers and the date stay fresh. */
    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }

    val state: StateFlow<HomeState> = combine(repo.schedules, repo.skippedDates, clock) { schedules, skipped, now ->
        val today = now.toLocalDate()
        val todays = schedules
            .filter { it.schedule.enabled && today.dayOfWeek in it.schedule.days }
            .flatMap { s ->
                s.reminders.map { r ->
                    TodayReminder(
                        reminderId = r.reminder.id,
                        time = r.reminder.time,
                        scheduleName = s.schedule.name,
                        stretchNames = r.stretches.map { it.name },
                        isPast = !r.reminder.time.isAfter(now.toLocalTime()),
                    )
                }
            }
            .sortedBy { it.time }
        HomeState(
            today = today,
            todaySkipped = today in skipped,
            todayReminders = todays,
            schedules = schedules,
            upcomingSkipCount = skipped.count { it.isAfter(today) },
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
}
