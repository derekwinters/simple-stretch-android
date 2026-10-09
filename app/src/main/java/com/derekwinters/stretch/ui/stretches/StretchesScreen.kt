package com.derekwinters.stretch.ui.stretches

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.data.Stretch
import com.derekwinters.stretch.notifications.Notifications
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StretchesViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as StretchApp).repository

    val stretches: StateFlow<List<Stretch>> =
        repo.stretches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(stretch: Stretch) {
        viewModelScope.launch { repo.saveStretch(stretch) }
    }

    fun delete(stretch: Stretch) {
        viewModelScope.launch { repo.deleteStretch(stretch) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StretchesScreen(
    onBack: () -> Unit,
    viewModel: StretchesViewModel = viewModel(),
) {
    val stretches by viewModel.stretches.collectAsStateWithLifecycle()
    // null = closed; Stretch with id 0 = new.
    var editing by remember { mutableStateOf<Stretch?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stretch library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Stretch(name = "") }) {
                Icon(Icons.Filled.Add, contentDescription = "Add stretch")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            items(stretches, key = { it.id }) { s ->
                ListItem(
                    modifier = Modifier.clickable { editing = s },
                    headlineContent = { Text(s.name) },
                    supportingContent = if (s.description.isNotBlank()) {
                        { Text(s.description, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    } else {
                        null
                    },
                    trailingContent = if (s.durationSeconds != null) {
                        { Text(Notifications.formatDuration(s.durationSeconds)) }
                    } else {
                        null
                    },
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { s ->
        StretchEditDialog(
            initial = s,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = if (s.id != 0L) {
                {
                    viewModel.delete(s)
                    editing = null
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun StretchEditDialog(
    initial: Stretch,
    onDismiss: () -> Unit,
    onSave: (Stretch) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var duration by remember { mutableStateOf(initial.durationSeconds?.toString() ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${initial.name}\"?") },
            text = { Text("It will also be removed from any reminder times that use it, and its goal and completion history will be deleted.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New stretch" else "Edit stretch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Instructions (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { v -> duration = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Duration in seconds (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete stretch") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            description = description.trim(),
                            durationSeconds = duration.toIntOrNull()?.takeIf { it > 0 },
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
