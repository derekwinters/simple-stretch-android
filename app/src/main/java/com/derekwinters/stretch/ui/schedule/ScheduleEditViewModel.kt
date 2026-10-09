package com.derekwinters.stretch.ui.schedule

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.data.ReminderDraft
import com.derekwinters.stretch.data.Schedule
import com.derekwinters.stretch.data.Stretch
import com.derekwinters.stretch.data.days
import com.derekwinters.stretch.data.time
import com.derekwinters.stretch.scheduling.DaysOfWeek
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

data class EditableReminder(
    val key: Long,
    val time: LocalTime,
    val stretchIds: Set<Long>,
)

class ScheduleEditViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repo = (application as StretchApp).repository

    val scheduleId: Long = savedStateHandle.get<Long>("scheduleId") ?: -1L
    val isNew: Boolean get() = scheduleId <= 0L

    var name by mutableStateOf("")
    var enabled by mutableStateOf(true)
    var days by mutableStateOf(DaysOfWeek.WEEKDAYS)
    val reminders = mutableStateListOf<EditableReminder>()
    var loading by mutableStateOf(!isNew)
        private set
    var saving by mutableStateOf(false)
        private set

    private var nextKey = 1L

    val allStretches: StateFlow<List<Stretch>> =
        repo.stretches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (!isNew) {
            viewModelScope.launch {
                repo.getSchedule(scheduleId)?.let { s ->
                    name = s.schedule.name
                    enabled = s.schedule.enabled
                    days = s.schedule.days
                    reminders.clear()
                    s.reminders
                        .sortedBy { it.reminder.minuteOfDay }
                        .forEach { r ->
                            reminders += EditableReminder(nextKey++, r.reminder.time, r.stretches.map { it.id }.toSet())
                        }
                }
                loading = false
            }
        }
    }

    val canSave: Boolean get() = name.isNotBlank() && !saving

    fun toggleDay(day: DayOfWeek) {
        days = if (day in days) days - day else days + day
    }

    fun addReminder(time: LocalTime) {
        reminders += EditableReminder(nextKey++, time, emptySet())
        sortReminders()
    }

    fun updateTime(key: Long, time: LocalTime) {
        val i = reminders.indexOfFirst { it.key == key }
        if (i >= 0) reminders[i] = reminders[i].copy(time = time)
        sortReminders()
    }

    fun updateStretches(key: Long, ids: Set<Long>) {
        val i = reminders.indexOfFirst { it.key == key }
        if (i >= 0) reminders[i] = reminders[i].copy(stretchIds = ids)
    }

    fun removeReminder(key: Long) {
        reminders.removeAll { it.key == key }
    }

    private fun sortReminders() {
        val sorted = reminders.sortedBy { it.time }
        reminders.clear()
        reminders.addAll(sorted)
    }

    fun save(onDone: () -> Unit) {
        if (!canSave) return
        saving = true
        viewModelScope.launch {
            repo.saveSchedule(
                Schedule(
                    id = if (isNew) 0L else scheduleId,
                    name = name.trim(),
                    enabled = enabled,
                    daysMask = DaysOfWeek.toMask(days),
                ),
                reminders.map { r ->
                    ReminderDraft(minuteOfDay = r.time.hour * 60 + r.time.minute, stretchIds = r.stretchIds.toList())
                },
            )
            saving = false
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        if (isNew) {
            onDone()
            return
        }
        viewModelScope.launch {
            repo.deleteSchedule(scheduleId)
            onDone()
        }
    }
}
