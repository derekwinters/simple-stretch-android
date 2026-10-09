package com.derekwinters.stretch.ui.common

import android.content.Context
import android.text.format.DateFormat
import com.derekwinters.stretch.scheduling.DaysOfWeek
import com.derekwinters.stretch.scheduling.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

object Formatting {
    fun time(context: Context, time: LocalTime): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun minuteOfDay(context: Context, minute: Int): String =
        time(context, LocalTime.of(minute / 60, minute % 60))

    /** HOME-003: e.g. "Every 60 min at :50, 8:00 AM - 5:00 PM". */
    fun repeatSummary(context: Context, rule: RepeatRule): String {
        val minute = rule.minutePastHour.toString().padStart(2, '0')
        val anchor = if (rule.intervalMinutes % 60 == 0) "at" else "from"
        return "Every ${rule.intervalMinutes} min $anchor :$minute, " +
            "${minuteOfDay(context, rule.windowStartMinute)} - ${minuteOfDay(context, rule.windowEndMinute)}"
    }

    fun date(date: LocalDate): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))

    fun shortDay(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    /** Days of the week in the order the user's locale expects (e.g. Sunday first in the US). */
    fun orderedDays(): List<DayOfWeek> {
        val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        return (0L until 7L).map { first.plus(it) }
    }

    fun daysSummary(days: Set<DayOfWeek>): String = when (days) {
        DaysOfWeek.ALL -> "Every day"
        DaysOfWeek.WEEKDAYS -> "Weekdays"
        DaysOfWeek.WEEKEND -> "Weekends"
        emptySet<DayOfWeek>() -> "No days selected"
        else -> orderedDays().filter { it in days }.joinToString(", ") { shortDay(it) }
    }
}
