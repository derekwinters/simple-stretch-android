package com.derekwinters.stretch.ui.schedule

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.derekwinters.stretch.scheduling.RepeatRule
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derekwinters.stretch.data.Stretch
import com.derekwinters.stretch.ui.common.Formatting
import com.derekwinters.stretch.ui.stretches.StretchEditDialog
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditScreen(
    onDone: () -> Unit,
    viewModel: ScheduleEditViewModel = viewModel(),
) {
    val context = LocalContext.current
    val stretches by viewModel.allStretches.collectAsStateWithLifecycle()

    // Which dialog is open: time picker for a reminder (key) or for a new one (-1), stretch picker.
    // Special keys for the repeating window's start and end times.
    var timeDialogFor by remember { mutableStateOf<Long?>(null) }
    var stretchDialogFor by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New schedule" else "Edit schedule") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!viewModel.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete schedule")
                        }
                    }
                    TextButton(
                        onClick = { viewModel.save(onDone) },
                        enabled = viewModel.canSave && !viewModel.loading,
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (!viewModel.loading) LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = { viewModel.name = it },
                    label = { Text("Name (e.g. Workday)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = viewModel.enabled, onCheckedChange = { viewModel.enabled = it })
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Formatting.orderedDays().forEach { day ->
                            FilterChip(
                                selected = day in viewModel.days,
                                onClick = { viewModel.toggleDay(day) },
                                label = { Text(Formatting.shortDay(day)) },
                            )
                        }
                    }
                    if (viewModel.days.isEmpty()) {
                        Text(
                            "Pick at least one day, or this schedule will never remind you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item {
                // SCHED-001: set times or repeating.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Reminders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !viewModel.repeating,
                            onClick = { viewModel.repeating = false },
                            label = { Text("Set times") },
                        )
                        FilterChip(
                            selected = viewModel.repeating,
                            onClick = { viewModel.repeating = true },
                            label = { Text("Repeating") },
                        )
                    }
                }
            }
            if (viewModel.repeating) {
                item {
                    RepeatingSection(
                        viewModel = viewModel,
                        onPickStart = { timeDialogFor = KEY_WINDOW_START },
                        onPickEnd = { timeDialogFor = KEY_WINDOW_END },
                    )
                }
            }
            if (!viewModel.repeating) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Reminder times",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { timeDialogFor = -1L }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Add time")
                    }
                }
            }
            if (!viewModel.repeating && viewModel.reminders.isEmpty()) {
                item {
                    Text(
                        "No reminder times yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!viewModel.repeating) items(viewModel.reminders, key = { it.key }) { r ->
                val names = stretches.filter { it.id in r.stretchIds }.map { it.name }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                Formatting.time(context, r.time),
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.clickable { timeDialogFor = r.key },
                            )
                            Text(
                                if (names.isEmpty()) "No set stretches (optional, tap to choose)" else names.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { stretchDialogFor = r.key }.padding(vertical = 4.dp),
                            )
                        }
                        IconButton(onClick = { viewModel.removeReminder(r.key) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove time")
                        }
                    }
                }
            }
        }
    }

    timeDialogFor?.let { key ->
        if (key == KEY_WINDOW_START || key == KEY_WINDOW_END) {
            val isStart = key == KEY_WINDOW_START
            TimePickerDialog(
                title = if (isStart) "From" else "Until",
                initial = if (isStart) viewModel.windowStart else viewModel.windowEnd,
                is24Hour = DateFormat.is24HourFormat(context),
                onDismiss = { timeDialogFor = null },
                onConfirm = { time ->
                    if (isStart) viewModel.windowStart = time else viewModel.windowEnd = time
                    timeDialogFor = null
                },
            )
        } else {
            val existing = viewModel.reminders.firstOrNull { it.key == key }
            TimePickerDialog(
                title = "Reminder time",
                initial = existing?.time ?: LocalTime.of(10, 0),
                is24Hour = DateFormat.is24HourFormat(context),
                onDismiss = { timeDialogFor = null },
                onConfirm = { time ->
                    if (existing == null) viewModel.addReminder(time) else viewModel.updateTime(key, time)
                    timeDialogFor = null
                },
            )
        }
    }

    stretchDialogFor?.let { key ->
        val existing = viewModel.reminders.firstOrNull { it.key == key }
        if (existing != null) {
            StretchPickerDialog(
                stretches = stretches,
                initial = existing.stretchIds,
                onDismiss = { stretchDialogFor = null },
                onConfirm = { ids ->
                    viewModel.updateStretches(key, ids)
                    stretchDialogFor = null
                },
                onCreateStretch = viewModel::createStretch,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete schedule?") },
            text = { Text("\"${viewModel.name}\" and all of its reminder times will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDone)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initial: LocalTime,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = is24Hour,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StretchPickerDialog(
    stretches: List<Stretch>,
    initial: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
    onCreateStretch: (Stretch, (Long) -> Unit) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    var creating by remember { mutableStateOf(false) }

    // LIB-007: the library's editor replaces the picker until saved or cancelled; the ticks are
    // remembered here, and the new stretch is ticked once it has its id.
    if (creating) {
        StretchEditDialog(
            initial = Stretch(name = ""),
            onDismiss = { creating = false },
            onSave = { newStretch ->
                creating = false
                onCreateStretch(newStretch) { newId -> selected = selected + newId }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stretches for this time") },
        text = {
            Column {
                if (stretches.isEmpty()) {
                    Text("Your stretch library is empty. Add one below.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(stretches, key = { it.id }) { s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (s.id in selected) selected - s.id else selected + s.id
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = s.id in selected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + s.id else selected - s.id
                                    },
                                )
                                Text(s.name)
                            }
                        }
                    }
                }
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("New stretch…")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private const val KEY_WINDOW_START = -2L
private const val KEY_WINDOW_END = -3L

private val INTERVAL_CHOICES = listOf(15, 20, 30, 45, 60, 90, 120)

/** SCHED-011 / SCHED-012: interval, minute past the hour, window, and a preview of the slots. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatingSection(
    viewModel: ScheduleEditViewModel,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
) {
    val context = LocalContext.current
    var minuteText by remember { mutableStateOf(viewModel.minutePastHour.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Every", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val choices = (INTERVAL_CHOICES + viewModel.intervalMinutes).distinct().sorted()
            choices.forEach { minutes ->
                FilterChip(
                    selected = viewModel.intervalMinutes == minutes,
                    onClick = { viewModel.intervalMinutes = minutes },
                    label = { Text(if (minutes % 60 == 0) "${minutes / 60} h" else "$minutes min") },
                )
            }
        }
        OutlinedTextField(
            value = minuteText,
            onValueChange = { v ->
                val digits = v.filter { it.isDigit() }.take(2)
                minuteText = digits
                digits.toIntOrNull()?.takeIf { it in 0..59 }?.let { viewModel.minutePastHour = it }
            },
            label = { Text("At minute past the hour (0-59)") },
            singleLine = true,
            isError = minuteText.toIntOrNull()?.let { it !in 0..59 } ?: true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("From", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onPickStart) { Text(Formatting.time(context, viewModel.windowStart)) }
            Text("until", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onPickEnd) { Text(Formatting.time(context, viewModel.windowEnd)) }
        }
        val slots = viewModel.previewSlots
        Text(
            when {
                viewModel.windowEnd.isBefore(viewModel.windowStart) -> "The end time must be after the start time."
                slots.isEmpty() -> "No reminder fits between these times."
                else -> "${slots.size} a day: " + slots.joinToString(", ") { Formatting.time(context, it) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (slots.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Reminders include the end time. Repeating reminders don't pick stretches; the stretch " +
                "session suggests your unmet goals instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (viewModel.intervalMinutes !in RepeatRule.MIN_INTERVAL..RepeatRule.MAX_INTERVAL) {
            Text("Pick an interval.", color = MaterialTheme.colorScheme.error)
        }
    }
}
