package com.derekwinters.stretch.ui.goals

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.data.Stretch
import com.derekwinters.stretch.goals.GoalMath
import com.derekwinters.stretch.goals.GoalRef
import com.derekwinters.stretch.goals.GoalRow
import com.derekwinters.stretch.goals.GoalStepper
import com.derekwinters.stretch.goals.StepperEdit
import com.derekwinters.stretch.goals.StepperTap
import com.derekwinters.stretch.goals.StretchRef
import com.derekwinters.stretch.ui.stretches.StretchEditDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class GoalsState(
    /** GOAL-002: every stretch, by name, with its goal (0 for none) and today's progress. */
    val rows: List<GoalRow> = emptyList(),
    val loaded: Boolean = false,
)

class GoalsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as StretchApp).repository

    /** Taps are applied one at a time, each planned from the stored goal (GOAL-002). */
    private val taps = Mutex()

    val state: StateFlow<GoalsState> = combine(
        repo.stretches,
        repo.goals,
        repo.countsOn(LocalDate.now()),
    ) { stretches, goals, counts ->
        GoalsState(
            GoalMath.goalRows(
                stretches.map { StretchRef(it.id, it.name) },
                goals.map { GoalRef(it.stretchId, it.timesPerDay) },
                counts,
            ),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsState())

    /**
     * GOAL-002 invariant: one tap is exactly one `setGoal` or `removeGoal` (each its own
     * GOAL-009 transaction). The count is read from the database after the previous tap has
     * been written, so quick repeated taps are never planned from a stale count.
     */
    fun tap(stretchId: Long, tap: StepperTap) {
        viewModelScope.launch {
            taps.withLock {
                val count = repo.goals.first().firstOrNull { it.stretchId == stretchId }?.timesPerDay ?: 0
                when (val edit = GoalStepper.plan(count, tap)) {
                    StepperEdit.None -> Unit
                    is StepperEdit.Set -> repo.setGoal(stretchId, edit.timesPerDay)
                    StepperEdit.Remove -> repo.removeGoal(stretchId)
                }
            }
        }
    }

    /** LIB-007: adds [stretch] to the library; it then appears in the list at 0. */
    fun createStretch(stretch: Stretch) {
        viewModelScope.launch { repo.saveStretch(stretch) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    viewModel: GoalsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily goals") },
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
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    "Set how many times a day you want to do each stretch. Once every goal is " +
                        "met for the day, reminders stay quiet until tomorrow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(state.rows, key = { it.stretchId }) { row ->
                ListItem(
                    headlineContent = { Text(row.stretchName) },
                    supportingContent = if (row.hasGoal) {
                        { GoalProgressText(row) }
                    } else {
                        null
                    },
                    trailingContent = {
                        GoalStepperControl(
                            row = row,
                            onTap = { tap -> viewModel.tap(row.stretchId, tap) },
                        )
                    },
                )
                HorizontalDivider()
            }
            if (state.loaded) {
                item {
                    // LIB-007: the library's editor; the new stretch then appears above at 0.
                    TextButton(
                        onClick = { creating = true },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("New stretch…")
                    }
                }
            }
        }
    }

    if (creating) {
        StretchEditDialog(
            initial = Stretch(name = ""),
            onDismiss = { creating = false },
            onSave = { newStretch ->
                creating = false
                viewModel.createStretch(newStretch)
            },
        )
    }
}

/** GOAL-002: "n of count today", with a ✓ in `primary` once the goal is met. */
@Composable
private fun GoalProgressText(row: GoalRow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${row.done} of ${row.count} today")
        if (row.met) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = "Goal met",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * GOAL-002: a compact − N + stepper. Colours are theme roles only (GOAL-002 invariant): with a
 * goal, `primaryContainer` / `onPrimaryContainer` (as the "New schedule" button); at 0,
 * `surfaceContainerHighest` (as the "Today's goals" card) with − disabled.
 */
@Composable
private fun GoalStepperControl(row: GoalRow, onTap: (StepperTap) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container = if (row.hasGoal) scheme.primaryContainer else scheme.surfaceContainerHighest
    val content = if (row.hasGoal) scheme.onPrimaryContainer else scheme.onSurface
    val buttonColors = IconButtonDefaults.iconButtonColors(contentColor = content)
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onTap(StepperTap.Minus) },
                enabled = GoalStepper.canDecrease(row.count),
                colors = buttonColors,
                modifier = Modifier.semantics { contentDescription = "Fewer ${row.stretchName}" },
            ) { Text("−", style = MaterialTheme.typography.titleMedium) }
            Text(
                "${row.count}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 24.dp),
            )
            IconButton(
                onClick = { onTap(StepperTap.Plus) },
                enabled = GoalStepper.canIncrease(row.count),
                colors = buttonColors,
                modifier = Modifier.semantics { contentDescription = "More ${row.stretchName}" },
            ) { Text("+", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
