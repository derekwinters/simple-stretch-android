package com.derekwinters.stretch.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * HOME-011: the Settings tab, a list of labelled items. It has no state of its own, so the tab and
 * its tests use this one composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(onOpenStretches: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            // HOME-004 invariant / LIB-006: the stretch library is reached by a labelled item.
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenStretches),
                    headlineContent = { Text("My stretches") },
                    supportingContent = { Text("Add, rename or remove stretches") },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
