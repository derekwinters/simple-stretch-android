package com.derekwinters.stretch.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag

/**
 * HOME-004: the bottom navigation bar's five items, in bar order. Every item has its [label],
 * always shown (HOME-004 invariant); [selectedIcon] and [icon] are its icon when selected and
 * not.
 */
enum class TopTab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    SCHEDULES(Routes.SCHEDULES, "Schedules", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    GOALS(Routes.GOALS, "Goals", Icons.Outlined.MilitaryTech, Icons.Filled.MilitaryTech),
    TRENDS(Routes.TRENDS, "Trends", Icons.AutoMirrored.Outlined.ShowChart, Icons.AutoMirrored.Filled.ShowChart),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
    ;

    companion object {
        /** The tab whose screen [route] is, or null for a screen opened above a tab (no bar). */
        fun forRoute(route: String?): TopTab? = entries.firstOrNull { it.route == route }

        /** Identifies an item of the bar in tests. */
        fun testTag(tab: TopTab): String = "tab-${tab.route}"
    }
}

/** HOME-004: the bottom bar, with [selected] highlighted. */
@Composable
fun StretchBottomBar(selected: TopTab, onSelect: (TopTab) -> Unit) {
    NavigationBar {
        TopTab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null) },
                // HOME-004 invariant: the label is shown on every item, selected or not.
                label = { Text(tab.label) },
                alwaysShowLabel = true,
                modifier = Modifier.testTag(TopTab.testTag(tab)),
            )
        }
    }
}
