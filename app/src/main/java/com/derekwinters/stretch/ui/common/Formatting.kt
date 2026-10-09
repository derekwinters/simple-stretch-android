package com.derekwinters.stretch.ui.common

import android.content.Context
import android.text.format.DateFormat
import com.derekwinters.stretch.scheduling.DaysOfWeek
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
