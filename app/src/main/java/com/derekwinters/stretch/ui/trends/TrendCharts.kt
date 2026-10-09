package com.derekwinters.stretch.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.derekwinters.stretch.trends.DayScore
import com.derekwinters.stretch.trends.DayStatus
import com.derekwinters.stretch.trends.IntradayStep
import com.derekwinters.stretch.trends.PeriodKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val ChartHeight: Dp = 180.dp
private val AxisWidth: Dp = 40.dp

/** "60%" for a 0..1 score. */
fun percent(score: Double): String = "${(score * 100).roundToInt()}%"

/** TREND-004: spoken and shown status of one day. */
fun dayStatusText(day: DayScore): String = when (day.status) {
    DayStatus.SCORED -> "${percent(day.score ?: 0.0)}, ${day.credited} of ${day.target}" +
        if (day.allMet) ", all goals met" else ""
    DayStatus.SKIPPED -> "skipped"
    DayStatus.NO_GOALS -> "no goals"
    DayStatus.FUTURE -> "not yet"
}

/** Left axis labels for a 0..100% chart. */
@Composable
private fun PercentAxis(height: Dp) {
    Box(Modifier.width(AxisWidth).height(height).clearAndSetSemantics { }) {
        val style = MaterialTheme.typography.labelSmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text("100%", style = style, color = color, modifier = Modifier.align(Alignment.TopStart))
        Text("50%", style = style, color = color, modifier = Modifier.align(Alignment.CenterStart))
        Text("0%", style = style, color = color, modifier = Modifier.align(Alignment.BottomStart))
    }
}

/**
 * TREND-004: one bar per day of the period, height = the day's score. Gaps (TREND-003) are drawn
 * as a dashed outline at the baseline, never as a 0% bar. Each bar is its own accessible,
 * clickable element with a content description; tapping a day up to today opens its detail.
 */
@Composable
fun DailyScoreChart(
    days: List<DayScore>,
    kind: PeriodKind,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val partial = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val gapColor = MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val todayColor = MaterialTheme.colorScheme.tertiary
    val longDate = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())

    Column(modifier) {
        Row {
            PercentAxis(ChartHeight)
            Box(Modifier.weight(1f).height(ChartHeight)) {
                // Grid lines at 0, 50 and 100%.
                Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                    for (fraction in listOf(0f, 0.5f, 1f)) {
                        val y = size.height * (1f - fraction)
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                }
                Row(Modifier.fillMaxSize()) {
                    days.forEach { day ->
                        val clickable = !day.date.isAfter(today)
                        val description = "${day.date.format(longDate)}: ${dayStatusText(day)}"
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    if (clickable) {
                                        Modifier.clickable(onClickLabel = "Show day", role = Role.Button) { onDayClick(day.date) }
                                    } else {
                                        Modifier
                                    },
                                )
                                .clearAndSetSemantics { contentDescription = description },
                        ) {
                            Canvas(Modifier.fillMaxSize().padding(horizontal = if (kind == PeriodKind.WEEK) 6.dp else 1.dp)) {
                                val score = day.score
                                val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                if (score != null) {
                                    // A sliver for 0% so a scored-but-empty day reads differently from a gap.
                                    val h = maxOf(size.height * score.toFloat(), 2.dp.toPx())
                                    drawRoundRect(
                                        color = if (day.allMet) primary else partial,
                                        topLeft = Offset(0f, size.height - h),
                                        size = Size(size.width, h),
                                        cornerRadius = radius,
                                    )
                                } else if (day.status != DayStatus.FUTURE) {
                                    val h = 6.dp.toPx()
                                    drawRoundRect(
                                        color = gapColor,
                                        topLeft = Offset(0f, size.height - h),
                                        size = Size(size.width, h),
                                        cornerRadius = radius,
                                        style = Stroke(
                                            width = 1.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                                        ),
                                    )
                                }
                                if (day.date == today) {
                                    drawLine(
                                        todayColor,
                                        Offset(0f, size.height),
                                        Offset(size.width, size.height),
                                        strokeWidth = 3.dp.toPx(),
                                        cap = StrokeCap.Round,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // Day labels: weekday names for a week; the 1st, 8th, 15th, 22nd and 29th for a month,
        // each centred under its bar but allowed to spread over its neighbours' narrow cells.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics { }) {
            Spacer(Modifier.width(AxisWidth))
            when (kind) {
                PeriodKind.WEEK -> days.forEach { day ->
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                PeriodKind.MONTH -> BoxWithConstraints(Modifier.weight(1f)) {
                    val cell = maxWidth / days.size.coerceAtLeast(1)
                    days.forEachIndexed { index, day ->
                        if ((day.date.dayOfMonth - 1) % 7 == 0) {
                            Text(
                                day.date.dayOfMonth.toString(),
                                modifier = Modifier.offset(x = cell * (index - 1)).width(cell * 3),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * TREND-008: cumulative score through the day as a step line, x = 00:00..24:00, y = 0..100%.
 * The line holds each value until the next completion and stops at [endSecond] (now, for today;
 * the end of the day otherwise). The chart as a whole has one content description.
 */
@Composable
fun IntradayChart(
    steps: List<IntradayStep>,
    endSecond: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        Row {
            PercentAxis(ChartHeight)
            Canvas(Modifier.weight(1f).height(ChartHeight).clearAndSetSemantics { }) {
                val daySeconds = 24 * 3600f
                fun x(second: Int) = size.width * (second / daySeconds).coerceIn(0f, 1f)
                fun y(score: Double) = size.height * (1f - score.toFloat().coerceIn(0f, 1f))

                for (fraction in listOf(0f, 0.5f, 1f)) {
                    val gy = size.height * (1f - fraction)
                    drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
                }
                // Vertical guides every 6 hours.
                for (h in listOf(6, 12, 18)) {
                    val gx = x(h * 3600)
                    drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1.dp.toPx())
                }
                if (steps.isEmpty()) return@Canvas

                val path = Path()
                path.moveTo(x(steps.first().secondOfDay), y(steps.first().score))
                for (i in 1 until steps.size) {
                    val s = steps[i]
                    path.lineTo(x(s.secondOfDay), y(steps[i - 1].score)) // hold, then
                    path.lineTo(x(s.secondOfDay), y(s.score)) // step up
                }
                val end = maxOf(endSecond, steps.last().secondOfDay)
                path.lineTo(x(end), y(steps.last().score))
                drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                for (s in steps.drop(1)) {
                    drawCircle(dotColor, radius = 3.5.dp.toPx(), center = Offset(x(s.secondOfDay), y(s.score)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Spacer(Modifier.width(AxisWidth))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("0:00", "6:00", "12:00", "18:00", "24:00").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
