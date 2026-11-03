package com.derekwinters.stretch.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Requirement IDs refer to docs/spec/screens.md. */
class TopTabTest {

    // HOME-004: five items in the order Home, Schedules, Goals, Trends, Settings, each labelled
    // (HOME-004 invariant: no unlabelled tab).
    @Test
    fun fiveLabelledTabsInOrder() {
        assertEquals(
            listOf("Home", "Schedules", "Goals", "Trends", "Settings"),
            TopTab.entries.map { it.label },
        )
        assertTrue(TopTab.entries.all { it.label.isNotBlank() })
    }

    // HOME-004: Home is the start destination; every tab has its own route.
    @Test
    fun homeIsTheStartAndRoutesAreDistinct() {
        assertEquals(Routes.HOME, TopTab.HOME.route)
        assertEquals(TopTab.entries.size, TopTab.entries.map { it.route }.toSet().size)
    }

    // HOME-004: only the five top-level routes show the bar; screens opened above them do not.
    @Test
    fun tabForRouteFindsOnlyTopLevelRoutes() {
        TopTab.entries.forEach { assertEquals(it, TopTab.forRoute(it.route)) }
        listOf(Routes.SCHEDULE, Routes.SKIPS, Routes.STRETCHES, Routes.SESSION, Routes.TREND_DAY, null)
            .forEach { assertEquals(null, TopTab.forRoute(it)) }
    }
}
