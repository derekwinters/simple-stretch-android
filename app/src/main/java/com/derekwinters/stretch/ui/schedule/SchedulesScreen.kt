package com.derekwinters.stretch.ui.schedule

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.data.ScheduleWithReminders
import com.derekwinters.stretch.data.days
import com.derekwinters.stretch.data.isRepeating
import com.derekwinters.stretch.data.repeatRule
import com.derekwinters.stretch.data.time
import com.derekwinters.stretch.ui.common.Formatting
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** HOME-010: every schedule, as the Schedules tab lists it. */
data class SchedulesState(
    val schedules: List<ScheduleWithReminders> = emptyList(),
    val loaded: Boolean = false,
)

class SchedulesViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as StretchApp).repository

    val state: StateFlow<SchedulesState> = repo.schedules
        .map { SchedulesState(it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SchedulesState())

    /** HOME-010 invariant: the repository reschedules as part of the toggle; there is no save. */
    fun setScheduleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repo.setScheduleEnabled(id, enabled) }
    }
}

/** HOME-010: the Schedules tab. */
@Composable
fun SchedulesScreen(
    onEditSchedule: (Long) -> Unit,
    onNewSchedule: () -> Unit,
    onOpenSkips: () -> Unit,
    viewModel: SchedulesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SchedulesContent(
        state = state,
        onEditSchedule = onEditSchedule,
        onNewSchedule = onNewSchedule,
        onOpenSkips = onOpenSkips,
        onSetScheduleEnabled = viewModel::setScheduleEnabled,
    )
}

/**
 * HOME-010: the Schedules tab drawn from its state alone: "Skip other days" at the top (SKIP-002),
 * every schedule with its days, times or repeat summary and its switch, and "New schedule".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesContent(
    state: SchedulesState,
    onEditSchedule: (Long) -> Unit,
    onNewSchedule: () -> Unit,
    onOpenSkips: () -> Unit,
    onSetScheduleEnabled: (id: Long, enabled: Boolean) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Schedules") }) },
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
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            // HOME-010 / SKIP-002: a labelled button, not an icon.
            item {
                OutlinedButton(
                    onClick = onOpenSkips,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Skip other days") }
            }
            if (state.loaded && state.schedules.isEmpty()) {
                item {
                    Text(
                        "No schedules yet. Tap \"New schedule\" to set up your first reminders.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(state.schedules, key = { it.schedule.id }) { s ->
                ScheduleRow(
                    item = s,
                    onClick = { onEditSchedule(s.schedule.id) },
                    onToggle = { onSetScheduleEnabled(s.schedule.id, it) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    item: ScheduleWithReminders,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val times = item.reminders.map { it.reminder.time }.sorted()
    val timing = when {
        item.schedule.isRepeating -> Formatting.repeatSummary(context, item.schedule.repeatRule)
        times.isEmpty() -> "No reminder times"
        else -> times.joinToString(", ") { Formatting.time(context, it) }
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(item.schedule.name) },
        supportingContent = { Text("${Formatting.daysSummary(item.schedule.days)} · $timing") },
        // HOME-010 invariant: toggling reschedules at once (SchedulesViewModel).
        trailingContent = { Switch(checked = item.schedule.enabled, onCheckedChange = onToggle) },
    )
}
