package com.derekwinters.stretch.ui.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derekwinters.stretch.trends.DayStatus
import com.derekwinters.stretch.trends.Period
import com.derekwinters.stretch.trends.PeriodKind
import com.derekwinters.stretch.ui.common.Formatting
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** TREND-004: e.g. "Oct 5 – 11, 2026", "Sep 28 – Oct 4, 2026" or "October 2026". */
private fun periodLabel(period: Period): String {
    val locale = Locale.getDefault()
    return when (period.kind) {
        PeriodKind.MONTH -> period.start.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        PeriodKind.WEEK -> {
            val start = period.start.format(DateTimeFormatter.ofPattern("MMM d", locale))
            val end = if (period.start.month == period.end.month) {
                period.end.format(DateTimeFormatter.ofPattern("d, yyyy", locale))
            } else {
                period.end.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))
            }
            "$start – $end"
        }
    }
}

private fun days(n: Int): String = if (n == 1) "1 day" else "$n days"

/** TREND-004..006: week/month daily-score chart with summary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    viewModel: TrendsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = if (state.period.kind == PeriodKind.WEEK) "week" else "month"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trends") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.period.kind == PeriodKind.WEEK,
                        onClick = { viewModel.setKind(PeriodKind.WEEK) },
                        label = { Text("Week") },
                    )
                    FilterChip(
                        selected = state.period.kind == PeriodKind.MONTH,
                        onClick = { viewModel.setKind(PeriodKind.MONTH) },
                        label = { Text("Month") },
                    )
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previous, enabled = state.canGoPrevious) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous $unit")
                    }
                    Text(
                        periodLabel(state.period),
                        modifier = Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = viewModel::next, enabled = state.canGoNext) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next $unit")
                    }
                }
            }
            if (state.loaded && state.firstGoalDay == null) {
                item {
                    Text(
                        "Set a daily goal to start seeing trends. Each day is scored by how much of " +
                            "that day's goals you completed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        label = "Average",
                        value = state.summary.average?.let { percent(it) } ?: "–",
                        detail = "over ${days(state.summary.countedDays)}",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "All goals met",
                        value = days(state.summary.allMetDays),
                        detail = "this $unit",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "Streak",
                        value = days(state.streak),
                        detail = "all goals met",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Daily score", style = MaterialTheme.typography.titleSmall)
                        DailyScoreChart(
                            days = state.days,
                            kind = state.period.kind,
                            today = state.today,
                            onDayClick = onOpenDay,
                        )
                        Text(
                            "Score = goal stretches done (up to each target) ÷ total target. Dashed " +
                                "days were skipped or had no goals and don't count. Tap a day for details.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** TREND-007/008: one day's goals, cumulative-score step chart and completion list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    onBack: () -> Unit,
    viewModel: DayDetailViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val title = state.date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val score = state.score
            if (state.loaded && score != null) {
                item {
                    Text(
                        when (score.status) {
                            DayStatus.SCORED -> "Score ${dayStatusText(score)}"
                            DayStatus.SKIPPED -> "This day was skipped, so it isn't scored."
                            DayStatus.NO_GOALS -> "No goals were set on this day, so it isn't scored."
                            DayStatus.FUTURE -> "This day hasn't happened yet."
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (state.goals.isNotEmpty()) {
                item { SectionTitle("Goals that day") }
                items(state.goals) { g ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.stretchName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${g.done}/${g.target}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (g.met) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (g.met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (g.done.toFloat() / g.target).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (state.steps.isNotEmpty()) {
                item { SectionTitle("Through the day") }
                item {
                    val endSecond = when {
                        state.date == LocalDate.now() -> LocalTime.now().toSecondOfDay()
                        state.date.isBefore(LocalDate.now()) -> 24 * 3600
                        else -> 0
                    }
                    val last = state.steps.last().score
                    val reached = state.steps.drop(1).joinToString("; ") { s ->
                        "${Formatting.time(context, LocalTime.ofSecondOfDay(s.secondOfDay.toLong()))} ${percent(s.score)}"
                    }
                    IntradayChart(
                        steps = state.steps,
                        endSecond = endSecond,
                        description = "Score through the day, ending at ${percent(last)}" +
                            if (reached.isEmpty()) "." else ": $reached.",
                    )
                }
            }
            item { SectionTitle("Completed stretches") }
            if (state.loaded && state.completions.isEmpty()) {
                item {
                    Text(
                        "Nothing logged on this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.completions) { c ->
                val time = Instant.ofEpochMilli(c.atMillis).atZone(zone).toLocalTime()
                ListItem(
                    leadingContent = { Text(Formatting.time(context, time), style = MaterialTheme.typography.titleSmall) },
                    headlineContent = { Text(c.stretchName) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}
