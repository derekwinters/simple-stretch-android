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
import com.derekwinters.stretch.data.ScheduleWithReminders
import com.derekwinters.stretch.data.ScheduleMode
import com.derekwinters.stretch.data.Stretch
import com.derekwinters.stretch.data.days
import com.derekwinters.stretch.data.time
import com.derekwinters.stretch.scheduling.DaysOfWeek
import com.derekwinters.stretch.scheduling.RepeatRule
import com.derekwinters.stretch.scheduling.RepeatingSlots
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

/**
 * The schedule editor's editable fields and the pure edits on them (SCHED-001, SCHED-011,
 * SCHED-012). [ScheduleEditViewModel] owns one and loads and saves it; the store screenshots
 * build one directly (STORE-SHOT-004). Compose state, so the editor recomposes on every edit.
 */
class ScheduleEditForm {
    var name by mutableStateOf("")
    var enabled by mutableStateOf(true)
    var days by mutableStateOf(DaysOfWeek.WEEKDAYS)
    val reminders = mutableStateListOf<EditableReminder>()

    /** SCHED-001 / SCHED-011: set times, or repeating. */
    var repeating by mutableStateOf(false)
    var windowStart by mutableStateOf(LocalTime.of(RepeatRule.DEFAULT_START / 60, RepeatRule.DEFAULT_START % 60))
    var windowEnd by mutableStateOf(LocalTime.of(RepeatRule.DEFAULT_END / 60, RepeatRule.DEFAULT_END % 60))
    var intervalMinutes by mutableStateOf(RepeatRule.DEFAULT_INTERVAL)
    var minutePastHour by mutableStateOf(RepeatRule.DEFAULT_MINUTE)

    private var nextKey = 1L

    val repeatRule: RepeatRule
        get() = RepeatRule(
            windowStartMinute = windowStart.hour * 60 + windowStart.minute,
            windowEndMinute = windowEnd.hour * 60 + windowEnd.minute,
            intervalMinutes = intervalMinutes,
            minutePastHour = minutePastHour,
        )

    /** SCHED-012: the slots the current repeating settings give, for the editor's preview. */
    val previewSlots: List<LocalTime> get() = RepeatingSlots.forDay(repeatRule)

    /** Whether the fields describe a schedule that can be saved (a save may still be running). */
    val isComplete: Boolean get() = name.isNotBlank() && (!repeating || previewSlots.isNotEmpty())

    /** Fills the form from a stored schedule, its set times sorted by time. */
    fun load(s: ScheduleWithReminders) {
        name = s.schedule.name
        enabled = s.schedule.enabled
        days = s.schedule.days
        repeating = s.schedule.mode == ScheduleMode.REPEATING
        windowStart = minuteToTime(s.schedule.windowStartMinute)
        windowEnd = minuteToTime(s.schedule.windowEndMinute)
        intervalMinutes = s.schedule.intervalMinutes
        minutePastHour = s.schedule.minutePastHour
        reminders.clear()
        s.reminders
            .sortedBy { it.reminder.minuteOfDay }
            .forEach { r ->
                reminders += EditableReminder(nextKey++, r.reminder.time, r.stretches.map { it.id }.toSet())
            }
    }

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
}

class ScheduleEditViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repo = (application as StretchApp).repository

    val scheduleId: Long = savedStateHandle.get<Long>("scheduleId") ?: -1L
    val isNew: Boolean get() = scheduleId <= 0L

    /** The fields being edited. */
    val form = ScheduleEditForm()

    var loading by mutableStateOf(!isNew)
        private set
    var saving by mutableStateOf(false)
        private set

    val allStretches: StateFlow<List<Stretch>> =
        repo.stretches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (!isNew) {
            viewModelScope.launch {
                repo.getSchedule(scheduleId)?.let { form.load(it) }
                loading = false
            }
        }
    }

    val canSave: Boolean get() = form.isComplete && !saving

    /** LIB-007: adds [stretch] to the library, then reports its new id (on the main thread). */
    fun createStretch(stretch: Stretch, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repo.saveStretch(stretch)) }
    }

    fun save(onDone: () -> Unit) {
        if (!canSave) return
        saving = true
        viewModelScope.launch {
            val f = form
            repo.saveSchedule(
                Schedule(
                    id = if (isNew) 0L else scheduleId,
                    name = f.name.trim(),
                    enabled = f.enabled,
                    daysMask = DaysOfWeek.toMask(f.days),
                    mode = if (f.repeating) ScheduleMode.REPEATING else ScheduleMode.FIXED,
                    windowStartMinute = f.repeatRule.windowStartMinute,
                    windowEndMinute = f.repeatRule.windowEndMinute,
                    intervalMinutes = f.intervalMinutes,
                    minutePastHour = f.minutePastHour,
                ),
                // SCHED-014: a repeating schedule keeps no set reminder times.
                if (f.repeating) {
                    emptyList()
                } else {
                    f.reminders.map { r ->
                        ReminderDraft(minuteOfDay = r.time.hour * 60 + r.time.minute, stretchIds = r.stretchIds.toList())
                    }
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

private fun minuteToTime(minute: Int): LocalTime {
    val m = minute.coerceIn(0, 24 * 60 - 1)
    return LocalTime.of(m / 60, m % 60)
}
