package com.derekwinters.stretch.ui

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.derekwinters.stretch.store.ShowcaseComposeTest
import com.derekwinters.stretch.store.ShowcaseFixture
import com.derekwinters.stretch.store.StoreShotDevice
import com.derekwinters.stretch.ui.goals.GoalsContent
import com.derekwinters.stretch.ui.home.HomeContent
import com.derekwinters.stretch.ui.home.HomeState
import com.derekwinters.stretch.ui.schedule.SchedulesContent
import com.derekwinters.stretch.ui.settings.SettingsContent
import com.derekwinters.stretch.ui.theme.StretchTheme
import com.derekwinters.stretch.ui.trends.TrendsContent
import com.derekwinters.stretch.widget.GoalRing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Requirement IDs refer to docs/spec/screens.md (HOME-). Drives the real [StretchNavHost] and
 * bottom bar with the top-level screens' real stateless content fed from [ShowcaseFixture], and
 * a marker text for each screen opened above them, so no database or StretchApp is involved.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [StoreShotDevice.SDK], qualifiers = StoreShotDevice.QUALIFIERS, application = Application::class)
class StretchNavHostTest : ShowcaseComposeTest() {

    private lateinit var nav: NavHostController

    private class ShowcaseTabs(private val home: HomeState) : StretchScreens {
        @Composable
        override fun Home(onOpenGoals: () -> Unit, onStretchNow: () -> Unit, onOpenSession: (String) -> Unit) =
            HomeContent(
                state = home,
                // HOME-005: both banners showing, so the test can look for them on other tabs.
                canNotify = false,
                canExact = false,
                clock = ShowcaseFixture.clock,
                onOpenGoals = onOpenGoals,
                onStretchNow = onStretchNow,
                onOpenSession = onOpenSession,
                onSetTodaySkipped = {},
            )

        @Composable
        override fun Schedules(onEditSchedule: (Long) -> Unit, onNewSchedule: () -> Unit, onOpenSkips: () -> Unit) =
            SchedulesContent(
                state = ShowcaseFixture.schedules(),
                onEditSchedule = onEditSchedule,
                onNewSchedule = onNewSchedule,
                onOpenSkips = onOpenSkips,
                onSetScheduleEnabled = { _, _ -> },
            )

        @Composable
        override fun Goals() = GoalsContent(state = ShowcaseFixture.goals(), onTap = { _, _ -> }, onNewStretch = {})

        @Composable
        override fun Trends(onOpenDay: (LocalDate) -> Unit) = TrendsContent(
            state = ShowcaseFixture.trends(),
            onOpenDay = onOpenDay,
            onSetKind = {},
            onPrevious = {},
            onNext = {},
        )

        @Composable
        override fun Settings(onOpenStretches: () -> Unit) = SettingsContent(onOpenStretches = onOpenStretches)

        @Composable
        override fun ScheduleEdit(onDone: () -> Unit) = Text("Schedule editor screen")

        @Composable
        override fun Stretches(onBack: () -> Unit) = Text("Stretch library screen")

        @Composable
        override fun Skips(onBack: () -> Unit) = Text("Skipped days screen")

        @Composable
        override fun TrendDay(onBack: () -> Unit) = Text("Trend day screen")

        @Composable
        override fun Session(onDone: () -> Unit) = Text("Session screen")
    }

    private fun show(home: HomeState = ShowcaseFixture.home()) {
        composeRule.setContent {
            nav = rememberNavController()
            StretchTheme(darkTheme = false, dynamicColor = false) {
                StretchNavHost(navController = nav, screens = ShowcaseTabs(home))
            }
        }
        composeRule.waitForIdle()
    }

    private fun route(): String? = nav.currentDestination?.route

    private fun tap(tab: TopTab) {
        composeRule.onNodeWithTag(TopTab.testTag(tab)).performClick()
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    // HOME-004: every item shows its icon's label, and the label stays shown when not selected
    // (HOME-004 invariant: every bar item is labelled).
    @Test
    fun everyTabIsLabelled() {
        show()
        TopTab.entries.forEach { tab ->
            composeRule.onNodeWithTag(TopTab.testTag(tab)).assert(hasText(tab.label)).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(TopTab.testTag(TopTab.HOME)).assertIsSelected()
        composeRule.onNodeWithTag(TopTab.testTag(TopTab.GOALS)).assertIsNotSelected()
    }

    // HOME-004: each top-level screen is one tap from every other.
    @Test
    fun everyTabIsOneTapFromEveryOther() {
        show()
        for (from in TopTab.entries) {
            for (to in TopTab.entries) {
                tap(from)
                assertEquals(from.route, route())
                tap(to)
                assertEquals("$from -> $to", to.route, route())
                composeRule.onNodeWithTag(TopTab.testTag(to)).assertIsSelected()
            }
        }
    }

    // HOME-004: Back on any other tab returns to Home, never to the tab before it.
    @Test
    fun backOnAnotherTabReturnsHome() {
        show()
        for (tab in TopTab.entries - TopTab.HOME) {
            tap(TopTab.SCHEDULES)
            tap(tab)
            pressBack()
            assertEquals("Back from $tab", Routes.HOME, route())
        }
    }

    // HOME-004: on Home the navigation host holds no Back callback, so Back goes to the system
    // and leaves the app; on another tab it does.
    @Test
    fun backOnHomeLeavesTheApp() {
        show()
        assertFalse(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        tap(TopTab.TRENDS)
        assertTrue(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        pressBack()
        assertEquals(Routes.HOME, route())
        assertFalse(composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    // HOME-005: the banners show on Home and on no other tab.
    @Test
    fun bannersOnlyOnHome() {
        show()
        composeRule.onNodeWithText(NOTIFICATIONS_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText(EXACT_ALARM_BANNER).assertIsDisplayed()
        for (tab in TopTab.entries - TopTab.HOME) {
            tap(tab)
            composeRule.onAllNodesWithText(NOTIFICATIONS_BANNER).assertCountEquals(0)
            composeRule.onAllNodesWithText(EXACT_ALARM_BANNER).assertCountEquals(0)
        }
    }

    // HOME-007: "Stretch now" under the ring opens the session (SESS-001), with no bar above it.
    @Test
    fun stretchNowOpensTheSession() {
        show()
        composeRule.onNodeWithText("Stretch now").performClick()
        composeRule.waitForIdle()
        assertEquals(Routes.SESSION, route())
        composeRule.onNodeWithText("Session screen").assertIsDisplayed()
        composeRule.onAllNodesWithText("Schedules").assertCountEquals(0)
        pressBack()
        assertEquals(Routes.HOME, route())
    }

    // HOME-006: with no goals, "Set a goal" beside "Stretch now" opens the Goals tab.
    @Test
    fun setAGoalOpensTheGoalsTab() {
        show(ShowcaseFixture.home().copy(ring = GoalRing.model(emptyList())))
        composeRule.onNodeWithText("Set a goal").performClick()
        composeRule.waitForIdle()
        assertEquals(TopTab.GOALS.route, route())
        composeRule.onNodeWithTag(TopTab.testTag(TopTab.GOALS)).assertIsSelected()
        pressBack()
        assertEquals(Routes.HOME, route())
    }

    // HOME-006: with goals set there is no "Set a goal" button.
    @Test
    fun noSetAGoalWhileGoalsExist() {
        show()
        composeRule.onAllNodesWithText("Set a goal").assertCountEquals(0)
        composeRule.onNodeWithText("Stretch now").assertIsDisplayed()
    }

    // HOME-010 / SKIP-002: "Skip other days" on the Schedules tab opens the skipped-days screen.
    @Test
    fun skipOtherDaysOpensSkippedDays() {
        show()
        tap(TopTab.SCHEDULES)
        composeRule.onNodeWithText("Skip other days").performClick()
        composeRule.waitForIdle()
        assertEquals(Routes.SKIPS, route())
        composeRule.onNodeWithText("Skipped days screen").assertIsDisplayed()
        pressBack()
        assertEquals(TopTab.SCHEDULES.route, route())
    }

    // HOME-011 / LIB-006: "My stretches" on the Settings tab opens the stretch library.
    @Test
    fun myStretchesOpensTheLibrary() {
        show()
        tap(TopTab.SETTINGS)
        composeRule.onNodeWithText("Add, rename or remove stretches").assertIsDisplayed()
        composeRule.onNodeWithText("My stretches").performClick()
        composeRule.waitForIdle()
        assertEquals(Routes.STRETCHES, route())
        composeRule.onNodeWithText("Stretch library screen").assertIsDisplayed()
    }

    // HOME-010: the schedule list with its switches and "New schedule" is on the Schedules tab,
    // and Home no longer lists schedules.
    @Test
    fun scheduleListIsOnTheSchedulesTab() {
        show()
        composeRule.onAllNodesWithText("New schedule", useUnmergedTree = true).assertCountEquals(0)
        tap(TopTab.SCHEDULES)
        composeRule.onNodeWithText("Desk breaks").assertIsDisplayed()
        composeRule.onNodeWithText("Workday").assertIsDisplayed()
        composeRule.onNodeWithText("New schedule", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(Routes.SCHEDULE, route())
    }

    // WIDGET-007: a request to open Home from another tab closes that tab and selects Home.
    @Test
    fun pendingHomeRouteSelectsHome() {
        var pending by mutableStateOf<String?>(null)
        composeRule.setContent {
            nav = rememberNavController()
            StretchTheme(darkTheme = false, dynamicColor = false) {
                StretchNavHost(
                    pendingRoute = pending,
                    onPendingRouteHandled = { pending = null },
                    navController = nav,
                    screens = ShowcaseTabs(ShowcaseFixture.home()),
                )
            }
        }
        composeRule.waitForIdle()
        tap(TopTab.SETTINGS)
        composeRule.onNodeWithText("My stretches").performClick()
        composeRule.waitForIdle()
        pending = Routes.HOME
        composeRule.waitForIdle()
        assertEquals(Routes.HOME, route())
        composeRule.onNodeWithTag(TopTab.testTag(TopTab.HOME)).assertIsSelected()
    }

    private companion object {
        const val NOTIFICATIONS_BANNER = "Notifications are off, so reminders can't be shown."
        const val EXACT_ALARM_BANNER = "Exact alarms aren't allowed, so reminders may arrive a few minutes late."
    }
}
