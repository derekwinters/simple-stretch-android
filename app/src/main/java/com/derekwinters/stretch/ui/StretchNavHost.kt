package com.derekwinters.stretch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
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

/** HOME-012: [NavMotion.SLIDE] in this window's pixels. */
@Composable
private fun slidePx(): Int = with(LocalDensity.current) { NavMotion.SLIDE.roundToPx() }

/** A value kept across recompositions that is only ever read where it is written. */
private class Kept<T>(var value: T)

/**
 * HOME-004: the bar under the top-level screens ([selected] is the shown tab, or null for a
 * screen opened above one, which has no bar). The bar is drawn once, here, outside the screens,
 * so a tab switch never moves or fades it (HOME-012 second invariant); it leaves and returns with
 * the shared axis X motion of the tab screen it belongs to (HOME-012).
 *
 * [content] fills the whole window, so it never changes height when the bar goes, and is handed
 * the padding a tab screen reserves for the bar ([TabPane]), kept while the bar is away.
 */
@Composable
fun BarScaffold(
    selected: TopTab?,
    onSelect: (TopTab) -> Unit,
    content: @Composable (tabPadding: PaddingValues) -> Unit,
) {
    val slide = slidePx()
    // While leaving, the bar still shows the tab it left from.
    val lastTab = remember { Kept(selected ?: TopTab.HOME) }
    if (selected != null) lastTab.value = selected
    val barHeight = remember { Kept(0.dp) }
    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = selected != null,
                // The bar only ever leaves forward into a full-window screen and returns by Back.
                enter = NavMotion.SHARED_AXIS_BACK.enter(slide),
                exit = NavMotion.SHARED_AXIS_FORWARD.exit(slide),
            ) {
                StretchBottomBar(selected ?: lastTab.value, onSelect)
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val bottom: Dp = padding.calculateBottomPadding()
        if (bottom > 0.dp) barHeight.value = bottom
        content(PaddingValues(bottom = barHeight.value))
    }
}

/**
 * HOME-004: a top-level screen inside [BarScaffold], kept clear of the bar by [tabPadding]. The
 * screen handles its own system-bar insets; only what the bar covers is consumed.
 */
@Composable
fun TabPane(tabPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(tabPadding).consumeWindowInsets(tabPadding)) { content() }
}

/**
 * HOME-004: one top-level screen with the bar, standing still. The store shots draw their tab
 * screens through this, the same frame and bar the navigation host draws.
 */
@Composable
fun TabScaffold(selected: TopTab, onSelect: (TopTab) -> Unit, content: @Composable () -> Unit) {
    BarScaffold(selected, onSelect) { tabPadding -> TabPane(tabPadding, content) }
}

/**
 * HOME-012: a destination with its enter, exit, pop-enter and pop-exit motion set explicitly
 * from [NavMotion], never the library's default cross-fade.
 */
private fun NavGraphBuilder.destination(
    route: String,
    slidePx: Int,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(
        route,
        arguments = arguments,
        enterTransition = { NavMotion.of(this, pop = false).enter(slidePx) },
        exitTransition = { NavMotion.of(this, pop = false).exit(slidePx) },
        popEnterTransition = { NavMotion.of(this, pop = true).enter(slidePx) },
        popExitTransition = { NavMotion.of(this, pop = true).exit(slidePx) },
    ) { content(it) }
}

/** The bar's space, for the tab destinations of [StretchNavHost] (from [BarScaffold]). */
private val LocalTabPadding = compositionLocalOf { PaddingValues(0.dp) }

/** HOME-004: a top-level destination, drawn in a [TabPane] clear of the bar. */
private fun NavGraphBuilder.tab(tab: TopTab, slidePx: Int, content: @Composable () -> Unit) =
    destination(tab.route, slidePx) { TabPane(LocalTabPadding.current, content) }

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
    val slide = slidePx()
    BarScaffold(
        selected = TopTab.forRoute(entry?.destination?.route),
        onSelect = { nav.navigateToTab(it) },
    ) { tabPadding ->
        // Read by each tab destination's TabPane, so the graph itself never depends on it.
        CompositionLocalProvider(LocalTabPadding provides tabPadding) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
                // HOME-012: the same motion as every destination's own, so nothing falls back to the
                // library's cross-fade.
                enterTransition = { NavMotion.of(this, pop = false).enter(slide) },
                exitTransition = { NavMotion.of(this, pop = false).exit(slide) },
                popEnterTransition = { NavMotion.of(this, pop = true).enter(slide) },
                popExitTransition = { NavMotion.of(this, pop = true).exit(slide) },
            ) {
                tab(TopTab.HOME, slide) {
                    screens.Home(
                        // HOME-006: "Set a goal" opens the Goals tab, as its bar item would.
                        onOpenGoals = { nav.navigateToTab(TopTab.GOALS) },
                        onStretchNow = { nav.navigate(Routes.session()) { launchSingleTop = true } },
                        // HOME-009: a Today row opens the route its notification would.
                        onOpenSession = { route -> nav.navigate(route) { launchSingleTop = true } },
                    )
                }
                tab(TopTab.SCHEDULES, slide) {
                    screens.Schedules(
                        onEditSchedule = { nav.navigate(Routes.schedule(it)) },
                        onNewSchedule = { nav.navigate(Routes.schedule(-1L)) },
                        onOpenSkips = { nav.navigate(Routes.SKIPS) },
                    )
                }
                tab(TopTab.GOALS, slide) { screens.Goals() }
                tab(TopTab.TRENDS, slide) {
                    screens.Trends(onOpenDay = { nav.navigate(Routes.trendDay(it)) })
                }
                tab(TopTab.SETTINGS, slide) {
                    screens.Settings(onOpenStretches = { nav.navigate(Routes.STRETCHES) })
                }
                destination(
                    Routes.SCHEDULE,
                    slide,
                    arguments = listOf(navArgument("scheduleId") { type = NavType.LongType }),
                ) {
                    screens.ScheduleEdit(onDone = { nav.popBackStack() })
                }
                destination(Routes.STRETCHES, slide) {
                    screens.Stretches(onBack = { nav.popBackStack() })
                }
                destination(Routes.SKIPS, slide) {
                    screens.Skips(onBack = { nav.popBackStack() })
                }
                destination(
                    Routes.TREND_DAY,
                    slide,
                    arguments = listOf(navArgument(DayDetailViewModel.ARG_EPOCH_DAY) { type = NavType.LongType }),
                ) {
                    screens.TrendDay(onBack = { nav.popBackStack() })
                }
                destination(
                    Routes.SESSION,
                    slide,
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
