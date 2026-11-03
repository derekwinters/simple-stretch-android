package com.derekwinters.stretch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.derekwinters.stretch.ui.goals.GoalsScreen
import com.derekwinters.stretch.ui.home.HomeScreen
import com.derekwinters.stretch.ui.schedule.ScheduleEditScreen
import com.derekwinters.stretch.ui.schedule.SchedulesScreen
import com.derekwinters.stretch.ui.session.SessionScreen
import com.derekwinters.stretch.ui.session.SessionViewModel
import com.derekwinters.stretch.ui.settings.SettingsContent
import com.derekwinters.stretch.ui.skips.SkipDaysScreen
import com.derekwinters.stretch.ui.stretches.StretchesScreen
import com.derekwinters.stretch.ui.trends.DayDetailScreen
import com.derekwinters.stretch.ui.trends.DayDetailViewModel
import com.derekwinters.stretch.ui.trends.TrendsScreen
import java.time.LocalDate

object Routes {
    const val HOME = "home"
    const val SCHEDULES = "schedules"
    const val SETTINGS = "settings"
    const val SCHEDULE = "schedule/{scheduleId}"
    const val STRETCHES = "stretches"
    const val SKIPS = "skips"
    const val GOALS = "goals"
    /** SESS-001: optional reminder origin (named stretch ids, schedule name, scheduled time). */
    const val SESSION = SessionRoute.PATTERN
    const val TRENDS = "trends"
    const val TREND_DAY = "trends/day/{${DayDetailViewModel.ARG_EPOCH_DAY}}"
    fun schedule(id: Long) = "schedule/$id"
    fun trendDay(date: LocalDate) = "trends/day/${date.toEpochDay()}"

    /** "Stretch now": no named list. */
    fun session() = SessionRoute.BASE
}

/**
 * The screens [StretchNavHost] shows, each given only its navigation callbacks. [AppScreens] is
 * the app's own; the navigation tests pass the screens' stateless content with showcase state,
 * so they run without the database.
 */
interface StretchScreens {
    /** HOME-001. */
    @Composable fun Home(onOpenGoals: () -> Unit, onStretchNow: () -> Unit, onOpenSession: (String) -> Unit)

    /** HOME-010. */
    @Composable fun Schedules(onEditSchedule: (Long) -> Unit, onNewSchedule: () -> Unit, onOpenSkips: () -> Unit)

    /** GOAL-002. */
    @Composable fun Goals()

    /** TREND-004. */
    @Composable fun Trends(onOpenDay: (LocalDate) -> Unit)

    /** HOME-011. */
    @Composable fun Settings(onOpenStretches: () -> Unit)

    @Composable fun ScheduleEdit(onDone: () -> Unit)

    @Composable fun Stretches(onBack: () -> Unit)

    @Composable fun Skips(onBack: () -> Unit)

    @Composable fun TrendDay(onBack: () -> Unit)

    @Composable fun Session(onDone: () -> Unit)
}

/** The app's screens, each fed by its ViewModel. */
object AppScreens : StretchScreens {
    @Composable
    override fun Home(onOpenGoals: () -> Unit, onStretchNow: () -> Unit, onOpenSession: (String) -> Unit) =
        HomeScreen(onOpenGoals = onOpenGoals, onStretchNow = onStretchNow, onOpenSession = onOpenSession)

    @Composable
    override fun Schedules(onEditSchedule: (Long) -> Unit, onNewSchedule: () -> Unit, onOpenSkips: () -> Unit) =
        SchedulesScreen(onEditSchedule = onEditSchedule, onNewSchedule = onNewSchedule, onOpenSkips = onOpenSkips)

    @Composable
    override fun Goals() = GoalsScreen()

    @Composable
    override fun Trends(onOpenDay: (LocalDate) -> Unit) = TrendsScreen(onOpenDay = onOpenDay)

    @Composable
    override fun Settings(onOpenStretches: () -> Unit) = SettingsContent(onOpenStretches = onOpenStretches)

    @Composable
    override fun ScheduleEdit(onDone: () -> Unit) = ScheduleEditScreen(onDone = onDone)

    @Composable
    override fun Stretches(onBack: () -> Unit) = StretchesScreen(onBack = onBack)

    @Composable
    override fun Skips(onBack: () -> Unit) = SkipDaysScreen(onBack = onBack)

    @Composable
    override fun TrendDay(onBack: () -> Unit) = DayDetailScreen(onBack = onBack)

    @Composable
    override fun Session(onDone: () -> Unit) = SessionScreen(onDone = onDone)
}

/**
 * HOME-004: opens [tab] the standard bottom-bar way: everything above Home is popped (its state
 * saved), so Back from any tab returns to Home and tabs never stack; the tab's own saved state is
 * restored; and re-selecting the shown tab opens no second copy.
 */
fun NavHostController.navigateToTab(tab: TopTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * HOME-004: the bar around a top-level screen, or nothing around a screen opened above one
 * ([selected] null). The navigation host and the store shots both draw it through this. The
 * screen inside handles its own system-bar insets; only what the bar covers is consumed.
 */
@Composable
fun TabScaffold(selected: TopTab?, onSelect: (TopTab) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        bottomBar = { if (selected != null) StretchBottomBar(selected, onSelect) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
    }
}

/**
 * [pendingRoute] is a screen requested from outside the UI (a notification, NOTIF-002/003, or
 * the widget, WIDGET-007); it is opened on top of home and then reported back through
 * [onPendingRouteHandled].
 */
@Composable
fun StretchNavHost(
    pendingRoute: String? = null,
    onPendingRouteHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    screens: StretchScreens = AppScreens,
) {
    val nav = navController
    val entry by nav.currentBackStackEntryAsState()
    TabScaffold(
        selected = TopTab.forRoute(entry?.destination?.route),
        onSelect = { nav.navigateToTab(it) },
    ) {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                screens.Home(
                    // HOME-006: "Set a goal" opens the Goals tab, as its bar item would.
                    onOpenGoals = { nav.navigateToTab(TopTab.GOALS) },
                    onStretchNow = { nav.navigate(Routes.session()) { launchSingleTop = true } },
                    // HOME-009: a Today row opens the route its notification would.
                    onOpenSession = { route -> nav.navigate(route) { launchSingleTop = true } },
                )
            }
            composable(Routes.SCHEDULES) {
                screens.Schedules(
                    onEditSchedule = { nav.navigate(Routes.schedule(it)) },
                    onNewSchedule = { nav.navigate(Routes.schedule(-1L)) },
                    onOpenSkips = { nav.navigate(Routes.SKIPS) },
                )
            }
            composable(Routes.GOALS) { screens.Goals() }
            composable(Routes.TRENDS) {
                screens.Trends(onOpenDay = { nav.navigate(Routes.trendDay(it)) })
            }
            composable(Routes.SETTINGS) {
                screens.Settings(onOpenStretches = { nav.navigate(Routes.STRETCHES) })
            }
            composable(
                Routes.SCHEDULE,
                arguments = listOf(navArgument("scheduleId") { type = NavType.LongType }),
            ) {
                screens.ScheduleEdit(onDone = { nav.popBackStack() })
            }
            composable(Routes.STRETCHES) {
                screens.Stretches(onBack = { nav.popBackStack() })
            }
            composable(Routes.SKIPS) {
                screens.Skips(onBack = { nav.popBackStack() })
            }
            composable(
                Routes.TREND_DAY,
                arguments = listOf(navArgument(DayDetailViewModel.ARG_EPOCH_DAY) { type = NavType.LongType }),
            ) {
                screens.TrendDay(onBack = { nav.popBackStack() })
            }
            composable(
                Routes.SESSION,
                arguments = listOf(
                    navArgument(SessionViewModel.ARG_IDS) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(SessionViewModel.ARG_SCHEDULE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(SessionViewModel.ARG_AT) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) {
                screens.Session(onDone = { nav.popBackStack() })
            }
        }
    }

    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        // Start from home, closing any other tab and any screen above it, so Back from the
        // session lands on home and the bar shows Home selected (WIDGET-007).
        nav.popBackStack(Routes.HOME, inclusive = false)
        // WIDGET-007: home itself only needs the screens above it closed.
        if (route != Routes.HOME) nav.navigate(route) { launchSingleTop = true }
        onPendingRouteHandled()
    }
}
