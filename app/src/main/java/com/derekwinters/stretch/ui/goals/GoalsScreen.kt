package com.derekwinters.stretch.ui.goals

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.data.Stretch
import com.derekwinters.stretch.goals.GoalMath
import com.derekwinters.stretch.goals.GoalProgress
import com.derekwinters.stretch.goals.GoalRef
import com.derekwinters.stretch.goals.StretchRef
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GoalsState(
    val goals: List<GoalProgress> = emptyList(),
    /** Stretches without a goal yet, for the "add" picker (GOAL-001). */
    val available: List<Stretch> = emptyList(),
    val loaded: Boolean = false,
)

class GoalsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as StretchApp).repository

    val state: StateFlow<GoalsState> = combine(
        repo.stretches,
        repo.goals,
        repo.countsOn(LocalDate.now()),
    ) { stretches, goals, counts ->
        val progress = GoalMath.progress(
            stretches.map { StretchRef(it.id, it.name) },
            goals.map { GoalRef(it.stretchId, it.timesPerDay) },
            counts,
        )
        val withGoal = goals.map { it.stretchId }.toSet()
        GoalsState(progress, stretches.filter { it.id !in withGoal }, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsState())

    fun setGoal(stretchId: Long, timesPerDay: Int) {
        viewModelScope.launch { repo.setGoal(stretchId, timesPerDay) }
    }

    fun removeGoal(stretchId: Long) {
        viewModelScope.launch { repo.removeGoal(stretchId) }
    }
}

/** What the goal dialog is editing: a new goal (stretch not chosen yet) or an existing one. */
private sealed interface GoalDialog {
    data object New : GoalDialog
    data class Edit(val goal: GoalProgress) : GoalDialog
}

private const val MAX_TIMES_PER_DAY = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    viewModel: GoalsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<GoalDialog?>(null) }

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
        floatingActionButton = {
            if (state.available.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = GoalDialog.New },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add goal") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Text(
                    "Set how many times a day you want to do a stretch. Reminders keep coming " +
                        "even after a goal is met.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (state.loaded && state.goals.isEmpty()) {
                item {
                    Text(
                        "No goals yet. Tap \"Add goal\", e.g. 3 hamstring stretches a day.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(state.goals, key = { it.stretchId }) { g ->
                ListItem(
                    modifier = Modifier.clickable { dialog = GoalDialog.Edit(g) },
                    headlineContent = { Text(g.stretchName) },
                    supportingContent = { Text("${g.target} a day") },
                    trailingContent = { Text("Today ${g.done}/${g.target}") },
                )
                HorizontalDivider()
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        GoalDialog.New -> GoalEditDialog(
            title = "New goal",
            stretches = state.available,
            initialStretchId = null,
            initialCount = 3,
            onDismiss = { dialog = null },
            onSave = { id, count ->
                viewModel.setGoal(id, count)
                dialog = null
            },
            onRemove = null,
        )
        is GoalDialog.Edit -> GoalEditDialog(
            title = d.goal.stretchName,
            stretches = emptyList(),
            initialStretchId = d.goal.stretchId,
            initialCount = d.goal.target,
            onDismiss = { dialog = null },
            onSave = { id, count ->
                viewModel.setGoal(id, count)
                dialog = null
            },
            onRemove = {
                viewModel.removeGoal(d.goal.stretchId)
                dialog = null
            },
        )
    }
}

/**
 * GOAL-002: pick a stretch (only when [initialStretchId] is null) and a count of 1 to 20.
 */
@Composable
private fun GoalEditDialog(
    title: String,
    stretches: List<Stretch>,
    initialStretchId: Long?,
    initialCount: Int,
    onDismiss: () -> Unit,
    onSave: (Long, Int) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var stretchId by remember { mutableStateOf(initialStretchId) }
    var count by remember { mutableIntStateOf(initialCount.coerceIn(1, MAX_TIMES_PER_DAY)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (initialStretchId == null) {
                    Text("Stretch", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(stretches, key = { it.id }) { s ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { stretchId = s.id },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = stretchId == s.id, onClick = { stretchId = s.id })
                                Text(s.name)
                            }
                        }
                    }
                }
                Text("Times per day", style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(onClick = { count-- }, enabled = count > 1) { Text("−") }
                    Text("$count", style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(onClick = { count++ }, enabled = count < MAX_TIMES_PER_DAY) { Text("+") }
                }
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text("Remove goal") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = stretchId != null,
                onClick = { stretchId?.let { onSave(it, count) } },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
