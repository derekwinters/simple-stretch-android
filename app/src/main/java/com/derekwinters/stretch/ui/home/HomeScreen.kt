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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derekwinters.stretch.R
import com.derekwinters.stretch.notifications.Notifications
import com.derekwinters.stretch.scheduling.ReminderScheduler
import com.derekwinters.stretch.ui.common.Formatting
import java.time.Clock

/**
 * HOME-001: the Home tab. [onOpenGoals] opens the Goals tab ("Set a goal", HOME-006),
 * [onStretchNow] the session with no named list (HOME-007), and [onOpenSession] the route a Today
 * row's notification would open (HOME-009).
 */
@Composable
fun HomeScreen(
    onOpenGoals: () -> Unit,
    onStretchNow: () -> Unit,
    onOpenSession: (route: String) -> Unit,
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

    HomeContent(
        state = state,
        canNotify = canNotify,
        canExact = canExact,
        clock = viewModel.clock,
        onOpenGoals = onOpenGoals,
        onStretchNow = onStretchNow,
        onOpenSession = onOpenSession,
        onSetTodaySkipped = viewModel::setTodaySkipped,
    )
}

/**
 * HOME-001..HOME-009: the home screen drawn from its state alone. [HomeScreen] feeds it from
 * [HomeViewModel] and the live permission checks; the store screenshots feed it directly
 * (STORE-SHOT-001). [clock] is the app clock: a Today row's time is taken in its zone, read at
 * the tap (HOME-009, SCHED-010 invariant). The bottom bar is drawn around it by the navigation
 * host (HOME-004).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeState,
    canNotify: Boolean,
    canExact: Boolean,
    clock: Clock,
    onOpenGoals: () -> Unit,
    onStretchNow: () -> Unit,
    onOpenSession: (route: String) -> Unit,
    onSetTodaySkipped: (skip: Boolean) -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        // HOME-004: the app's name from app_name, no icon actions and no overflow menu.
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        val today = state.todayList
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // HOME-005: the banners, on Home only.
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

            // HOME-001: today's date.
            item {
                Text(
                    Formatting.date(state.today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // HOME-006 / HOME-007: the widget's ring, "Stretch now" under it, and "Set a goal"
            // beside it while no goal is set.
            item {
                RingSection(state = state, onStretchNow = onStretchNow, onOpenGoals = onOpenGoals)
            }

            // SKIP-001 / HOME-002: skip or resume today from the right of the Today heading.
            item {
                TodayHeaderRow(
                    button = TodaySkipButton.of(state.todaySkipped, state.todayReminders),
                    onClick = { skip -> onSetTodaySkipped(skip) },
                )
            }
            if (state.loaded && today.isEmpty) {
                item {
                    Text(
                        "No reminders scheduled for today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // HOME-002: upcoming reminders first ...
            items(today.upcoming, key = { "today-${it.reminder.key}" }) { row ->
                // HOME-009: every row, past or skipped too, opens what its notification would.
                TodayReminderRow(
                    row.reminder,
                    greyed = row.greyed,
                    onClick = { onOpenSession(row.reminder.sessionRoute(state.today, clock.zone)) },
                )
            }
            // ... then, after a divider, today's past reminders, greyed.
            if (today.past.isNotEmpty()) {
                if (today.upcoming.isNotEmpty()) {
                    item(key = "today-divider") { HorizontalDivider() }
                }
                items(today.past, key = { "today-${it.reminder.key}" }) { row ->
                    TodayReminderRow(
                        row.reminder,
                        greyed = row.greyed,
                        onClick = { onOpenSession(row.reminder.sessionRoute(state.today, clock.zone)) },
                    )
                }
            }
        }
    }
}

/** HOME-006 / HOME-007: the ring, centred, with its buttons under it. */
@Composable
private fun RingSection(state: HomeState, onStretchNow: () -> Unit, onOpenGoals: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(RING_SIZE)) {
            // Drawn once loaded, so a slow first read never flashes the no-goals ring.
            if (state.loaded) GoalRingImage(state.ring, Modifier.size(RING_SIZE))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onStretchNow) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Stretch now")
            }
            if (state.loaded && HomeRing.showsSetAGoal(state.ring)) {
                OutlinedButton(onClick = onOpenGoals) { Text("Set a goal") }
            }
        }
    }
}

private val RING_SIZE = 168.dp

/**
 * HOME-002: the "Today" heading with the skip/resume text button at its right end. The button is
 * always present (HOME-002 invariant: "Resume today" is never hidden while today is skipped).
 */
@Composable
private fun TodayHeaderRow(button: TodaySkipButton, onClick: (skip: Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Today",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onClick(button.skipsOnTap) }, enabled = button.enabled) {
            Text(button.label)
        }
    }
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
private fun TodayReminderRow(r: TodayReminder, greyed: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val color = if (greyed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
    ListItem(
        // HOME-009: announced as a button that opens the stretch session.
        modifier = Modifier.clickable(onClickLabel = "Open stretch session", role = Role.Button, onClick = onClick),
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
