package com.derekwinters.stretch.ui.session

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.goals.ReminderOrigin
import com.derekwinters.stretch.goals.SessionEntry
import com.derekwinters.stretch.goals.SessionHeadings
import com.derekwinters.stretch.goals.SessionLayout
import com.derekwinters.stretch.goals.SessionTop
import com.derekwinters.stretch.ui.SessionRoute
import com.derekwinters.stretch.ui.common.Formatting
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

class SessionViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application as StretchApp
    private val repo = app.repository

    /** SCHED-016: today and the reminder's local time are read from the app clock. */
    private val clock = app.clock

    /** SESS-001: what the opening reminder named, if anything. */
    private val reminderStretchIds: List<Long> = ReminderOrigin.parseIds(savedStateHandle.get<String>(ARG_IDS))
    private val scheduleName: String? = savedStateHandle.get<String>(ARG_SCHEDULE)
    private val scheduledAt: Long = savedStateHandle.get<Long>(ARG_AT) ?: -1L

    /** SESS-002/005: both sections are read once, so nothing moves while the screen is shown. */
    var layout by mutableStateOf(SessionLayout(null, emptyList(), emptyList()))
        private set
    var loading by mutableStateOf(true)
        private set

    /** SESS-002 invariant: nothing is pre-checked, however the screen was opened. */
    var checked by mutableStateOf<Set<Long>>(emptySet())
        private set
    var saving by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val named = if (scheduleName != null && scheduledAt >= 0) reminderStretchIds else null
            layout = repo.sessionLayout(LocalDate.now(clock), named)
            loading = false
        }
    }

    /** SESS-002: the top section's heading, or null when there is no top section. */
    fun topHeading(): String? = when (layout.top) {
        SessionTop.FROM_REMINDER -> {
            val time = Instant.ofEpochMilli(scheduledAt).atZone(clock.zone).toLocalTime()
            SessionHeadings.fromReminder(Formatting.time(getApplication<Application>(), time), scheduleName.orEmpty())
        }
        SessionTop.GOALS_SHORT -> SessionHeadings.GOALS_SHORT
        null -> null
    }

    fun toggle(stretchId: Long) {
        checked = if (stretchId in checked) checked - stretchId else checked + stretchId
    }

    /** SESS-003: one completion per checked stretch, in either section, then close. */
    fun save(onDone: () -> Unit) {
        if (checked.isEmpty() || saving) return
        saving = true
        viewModelScope.launch {
            repo.logCompletions(checked)
            saving = false
            onDone()
        }
    }

    companion object {
        const val ARG_IDS = SessionRoute.ARG_IDS
        const val ARG_SCHEDULE = SessionRoute.ARG_SCHEDULE
        const val ARG_AT = SessionRoute.ARG_AT
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    onDone: () -> Unit,
    viewModel: SessionViewModel = viewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stretch session") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onDone) },
                        enabled = viewModel.checked.isNotEmpty() && !viewModel.saving,
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (!viewModel.loading) {
            val layout = viewModel.layout
            val topHeading = viewModel.topHeading()
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (layout.topEntries.isEmpty() && layout.others.isEmpty()) {
                    item {
                        Text(
                            "Your stretch library is empty. Add stretches from the library screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                // SESS-002: the reminder's stretches, or the goals still short, highlighted.
                if (topHeading != null) {
                    item(key = "top-heading") {
                        SectionHeading(topHeading, MaterialTheme.colorScheme.primary)
                    }
                    items(layout.topEntries, key = { it.stretchId }) { entry ->
                        SessionRow(
                            entry = entry,
                            checked = entry.stretchId in viewModel.checked,
                            highlighted = true,
                            onToggle = { viewModel.toggle(entry.stretchId) },
                        )
                    }
                }
                // SESS-002: every other stretch, alphabetical, always expanded.
                if (layout.others.isNotEmpty()) {
                    if (topHeading != null) {
                        item(key = "others-heading") {
                            SectionHeading(SessionHeadings.OTHERS, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(layout.others, key = { it.stretchId }) { entry ->
                        SessionRow(
                            entry = entry,
                            checked = entry.stretchId in viewModel.checked,
                            highlighted = false,
                            onToggle = { viewModel.toggle(entry.stretchId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String, color: Color) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SessionRow(entry: SessionEntry, checked: Boolean, highlighted: Boolean, onToggle: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        colors = if (highlighted) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle() }) },
        headlineContent = { Text(entry.name) },
        supportingContent = { Text(progressLabel(entry)) },
    )
    HorizontalDivider()
}

/** SESS-002: "n of target today" for goals; nothing special otherwise. */
private fun progressLabel(entry: SessionEntry): String {
    val target = entry.target ?: return if (entry.done > 0) "Done ${entry.done}× today · no goal" else "No goal"
    return if (entry.done >= target) {
        "Goal met: ${entry.done} of $target today"
    } else {
        "${entry.done} of $target today"
    }
}
