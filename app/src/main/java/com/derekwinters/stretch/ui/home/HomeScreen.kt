package com.derekwinters.stretch.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derekwinters.stretch.data.ScheduleWithReminders
import com.derekwinters.stretch.data.days
import com.derekwinters.stretch.data.isRepeating
import com.derekwinters.stretch.data.repeatRule
import com.derekwinters.stretch.data.time
import com.derekwinters.stretch.goals.GoalProgress
import com.derekwinters.stretch.notifications.Notifications
import com.derekwinters.stretch.scheduling.ReminderScheduler
import com.derekwinters.stretch.ui.common.Formatting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onEditSchedule: (Long) -> Unit,
    onNewSchedule: () -> Unit,
    onOpenStretches: () -> Unit,
    onOpenSkips: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenTrends: () -> Unit,
    onStretchNow: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var canNotify by remember { mutableStateOf(Notifications.canPost(context)) }
    var canExact by remember { mutableStateOf(ReminderScheduler.canScheduleExact(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canNotify = Notifications.canPost(context)
        canExact = ReminderScheduler.canScheduleExact(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { canNotify = Notifications.canPost(context) }

    // NOTIF-005: ask for the notification permission once, on first launch, on Android 13+.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canNotify) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stretch") },
                actions = {
                    IconButton(onClick = onOpenGoals) {
                        Icon(Icons.Filled.Star, contentDescription = "Daily goals")
                    }
                    // HOME-004: one icon plus a labelled overflow menu. HOME-008: "Skip today"
                    // stays reachable here while the skip card is closed.
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (state.todaySkipped) "Resume today" else "Skip today") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.setTodaySkipped(!state.todaySkipped)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Skip other days") },
                                onClick = {
                                    menuOpen = false
                                    onOpenSkips()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("My stretches") },
                                onClick = {
                                    menuOpen = false
                                    onOpenStretches()
                                },
                            )
                            // TREND-009: trends are reached by a labelled menu item.
                            DropdownMenuItem(
                                text = { Text("Trends") },
                                onClick = {
                                    menuOpen = false
                                    onOpenTrends()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewSchedule,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New schedule") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!canNotify) {
                item {
                    WarningCard(
                        text = "Notifications are off, so reminders can't be shown.",
                        action = "Enable",
                        onAction = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        },
                    )
                }
            }
            if (!canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    WarningCard(
                        text = "Exact alarms aren't allowed, so reminders may arrive a few minutes late.",
                        action = "Allow",
                        onAction = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        },
                    )
                }
            }

            // HOME-008 invariant: a skipped day always shows the card ("Resume today").
            if (state.todaySkipped || !state.skipCardClosedToday) {
                item {
                    SkipTodayCard(
                        state = state,
                        onSkip = { viewModel.setTodaySkipped(true) },
                        onUnskip = { viewModel.setTodaySkipped(false) },
                        onOpenSkips = onOpenSkips,
                        onClose = { viewModel.closeSkipCard() },
                    )
                }
            }

            item {
                GoalsCard(
                    goals = state.goals,
                    loaded = state.loaded,
                    onStretchNow = onStretchNow,
                    onOpenGoals = onOpenGoals,
                    onOpenTrends = onOpenTrends,
                )
            }

            item { SectionTitle("Today") }
            if (state.loaded && state.todayReminders.isEmpty()) {
                item {
                    Text(
                        "No reminders scheduled for today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.todayReminders, key = { "today-${it.key}" }) { r ->
                TodayReminderRow(r, dimmed = r.isPast || state.todaySkipped)
            }

            item { SectionTitle("Schedules") }
            if (state.loaded && state.schedules.isEmpty()) {
                item {
                    Text(
                        "No schedules yet. Tap \"New schedule\" to set up your first reminders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.schedules, key = { "schedule-${it.schedule.id}" }) { s ->
                ScheduleCard(
                    item = s,
                    onClick = { onEditSchedule(s.schedule.id) },
                    onToggle = { viewModel.setScheduleEnabled(s.schedule.id, it) },
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
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun WarningCard(text: String, action: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun SkipTodayCard(
    state: HomeState,
    onSkip: () -> Unit,
    onUnskip: () -> Unit,
    onOpenSkips: () -> Unit,
    onClose: () -> Unit,
) {
    val skipped = state.todaySkipped
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (skipped) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Formatting.date(state.today),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                // HOME-008: closable for today only; never while today is skipped.
                if (!skipped) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Hide until tomorrow")
                    }
                }
            }
            Text(
                if (skipped) "Today is skipped. No stretch reminders today." else "Can't stretch today?",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (skipped) {
                    OutlinedButton(onClick = onUnskip) { Text("Resume today") }
                } else {
                    Button(onClick = onSkip) { Text("Skip today") }
                }
                TextButton(onClick = onOpenSkips) {
                    Text(
                        if (state.upcomingSkipCount > 0) {
                            "Skip other days (${state.upcomingSkipCount} planned)"
                        } else {
                            "Skip other days"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayReminderRow(r: TodayReminder, dimmed: Boolean) {
    val context = LocalContext.current
    val color = if (dimmed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
    ListItem(
        leadingContent = {
            Text(Formatting.time(context, r.time), style = MaterialTheme.typography.titleMedium, color = color)
        },
        headlineContent = {
            val rule = r.repeatRule
            val text = when {
                rule == null -> r.stretchNames.ifEmpty { listOf("Stretch break") }.joinToString(", ")
                r.isPast -> "Stretch break · none left today"
                else -> "Stretch break · ${r.slotsLeft} left today"
            }
            Text(text, color = color)
        },
        supportingContent = {
            val rule = r.repeatRule
            Text(
                if (rule == null) r.scheduleName else "${r.scheduleName} · ${Formatting.repeatSummary(context, rule)}",
                color = color,
            )
        },
    )
}

@Composable
private fun ScheduleCard(
    item: ScheduleWithReminders,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.schedule.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    Formatting.daysSummary(item.schedule.days),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val times = item.reminders.map { it.reminder.time }.sorted()
                Text(
                    when {
                        item.schedule.isRepeating -> Formatting.repeatSummary(context, item.schedule.repeatRule)
                        times.isEmpty() -> "No reminder times"
                        else -> times.joinToString("  ·  ") { Formatting.time(context, it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = item.schedule.enabled, onCheckedChange = onToggle)
        }
    }
}

/** HOME-006 / HOME-007 / TREND-009: today's progress, "Stretch now", links to goals and trends. */
@Composable
private fun GoalsCard(
    goals: List<GoalProgress>,
    loaded: Boolean,
    onStretchNow: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenTrends: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's goals", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // TREND-009: in the card's header, so the button row below still fits a phone.
                if (goals.isNotEmpty()) TextButton(onClick = onOpenTrends) { Text("Trends") }
            }
            if (loaded && goals.isEmpty()) {
                Text(
                    "Set daily goals, e.g. 3 hamstring stretches a day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            goals.forEach { g ->
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
                        progress = { g.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStretchNow) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Stretch now")
                }
                TextButton(onClick = onOpenGoals) { Text(if (goals.isEmpty()) "Set goals" else "Edit goals") }
            }
        }
    }
}
