package com.derekwinters.stretch.ui.home

import com.derekwinters.stretch.data.ScheduleWithReminders
import com.derekwinters.stretch.data.days
import com.derekwinters.stretch.data.isRepeating
import com.derekwinters.stretch.data.repeatRule
import com.derekwinters.stretch.data.time
import com.derekwinters.stretch.scheduling.RepeatRule
import com.derekwinters.stretch.scheduling.RepeatingSlots
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One row of HOME-002. A set reminder time has [stretchNames] and their [stretchIds], in the
 * reminder's order (HOME-009); a repeating schedule has a [repeatRule], no stretches, and shows
 * its next slot today (or its last, once all have passed).
 */
data class TodayReminder(
    val key: String,
    val time: LocalTime,
    val scheduleName: String,
    val stretchNames: List<String>,
    val isPast: Boolean,
    val repeatRule: RepeatRule? = null,
    val slotsLeft: Int = 0,
    val stretchIds: List<Long> = emptyList(),
)

object TodayReminders {
    /**
     * HOME-002: today's rows from every enabled schedule that selects today, in time order. [now]
     * is the local date and time read from the app clock (SCHED-016); a set time at or before it
     * is past, and a repeating schedule is past once all its slots today are.
     */
    fun of(schedules: List<ScheduleWithReminders>, now: LocalDateTime): List<TodayReminder> {
        val today = now.toLocalDate()
        val nowTime = now.toLocalTime()
        return schedules
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
                            // HOME-009: the same ids, in the same order, as its notification.
                            stretchIds = r.stretches.map { it.id },
                        )
                    }
                }
            }
            .sortedBy { it.time }
    }
}
