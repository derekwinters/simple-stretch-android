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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.goals.SessionEntry
import kotlinx.coroutines.launch
import java.time.LocalDate

class SessionViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as StretchApp).repository

    /** SESS-005: read once, so the order never changes while the screen is shown. */
    var entries by mutableStateOf<List<SessionEntry>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var checked by mutableStateOf<Set<Long>>(emptySet())
        private set
    var saving by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            entries = repo.sessionEntries(LocalDate.now())
            loading = false
        }
    }

    fun toggle(stretchId: Long) {
        checked = if (stretchId in checked) checked - stretchId else checked + stretchId
    }

    /** SESS-003: one completion per checked stretch, then close. */
    fun save(onDone: () -> Unit) {
        if (checked.isEmpty() || saving) return
        saving = true
        viewModelScope.launch {
            repo.logCompletions(checked)
            saving = false
            onDone()
        }
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Text(
                        if (viewModel.entries.isEmpty()) {
                            "Your stretch library is empty. Add stretches from the library screen."
                        } else {
                            "Check what you stretched, then tap Save."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(viewModel.entries, key = { it.stretchId }) { entry ->
                    val isChecked = entry.stretchId in viewModel.checked
                    ListItem(
                        modifier = Modifier.clickable { viewModel.toggle(entry.stretchId) },
                        leadingContent = {
                            Checkbox(checked = isChecked, onCheckedChange = { viewModel.toggle(entry.stretchId) })
                        },
                        headlineContent = { Text(entry.name) },
                        supportingContent = { Text(progressLabel(entry)) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
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
