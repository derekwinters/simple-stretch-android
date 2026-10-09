package com.derekwinters.stretch.ui.skips

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.derekwinters.stretch.StretchApp
import com.derekwinters.stretch.ui.common.Formatting
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SkipDaysViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as StretchApp).repository

    /** Today and future skipped dates, soonest first. */
    val upcoming: StateFlow<List<LocalDate>> = repo.skippedDates
        .map { dates ->
            val today = LocalDate.now()
            dates.filter { !it.isBefore(today) }.sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(date: LocalDate) {
        viewModelScope.launch { repo.skipDate(date) }
    }

    fun remove(date: LocalDate) {
        viewModelScope.launch { repo.unskipDate(date) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipDaysScreen(
    onBack: () -> Unit,
    viewModel: SkipDaysViewModel = viewModel(),
) {
    val dates by viewModel.upcoming.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skipped days") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picking = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Skip a day") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item {
                Text(
                    "No stretch reminders fire on these days. Past days are cleared automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (dates.isEmpty()) {
                item {
                    Text(
                        "No days skipped.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(dates, key = { it.toEpochDay() }) { date ->
                ListItem(
                    headlineContent = { Text(Formatting.date(date)) },
                    supportingContent = if (date == LocalDate.now()) {
                        { Text("Today") }
                    } else {
                        null
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.remove(date) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove skip")
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (picking) {
        // Material 3's DatePicker works in UTC-midnight milliseconds.
        val todayUtcMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = todayUtcMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            viewModel.add(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        picking = false
                    },
                ) { Text("Skip") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}
