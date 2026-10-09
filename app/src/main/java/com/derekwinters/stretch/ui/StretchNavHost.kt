package com.derekwinters.stretch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.derekwinters.stretch.ui.goals.GoalsScreen
import com.derekwinters.stretch.ui.home.HomeScreen
import com.derekwinters.stretch.ui.schedule.ScheduleEditScreen
import com.derekwinters.stretch.ui.session.SessionScreen
import com.derekwinters.stretch.ui.skips.SkipDaysScreen
import com.derekwinters.stretch.ui.stretches.StretchesScreen

object Routes {
    const val HOME = "home"
    const val SCHEDULE = "schedule/{scheduleId}"
    const val STRETCHES = "stretches"
    const val SKIPS = "skips"
    const val GOALS = "goals"
    const val SESSION = "session"
    fun schedule(id: Long) = "schedule/$id"
}

/**
 * [pendingRoute] is a screen requested from outside the UI (a notification, NOTIF-002/003); it
 * is opened on top of home and then reported back through [onPendingRouteHandled].
 */
@Composable
fun StretchNavHost(
    pendingRoute: String? = null,
    onPendingRouteHandled: () -> Unit = {},
) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onEditSchedule = { nav.navigate(Routes.schedule(it)) },
                onNewSchedule = { nav.navigate(Routes.schedule(-1L)) },
                onOpenStretches = { nav.navigate(Routes.STRETCHES) },
                onOpenSkips = { nav.navigate(Routes.SKIPS) },
                onOpenGoals = { nav.navigate(Routes.GOALS) },
                onStretchNow = { nav.navigate(Routes.SESSION) { launchSingleTop = true } },
            )
        }
        composable(
            Routes.SCHEDULE,
            arguments = listOf(navArgument("scheduleId") { type = NavType.LongType }),
        ) {
            ScheduleEditScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.STRETCHES) {
            StretchesScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SKIPS) {
            SkipDaysScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.GOALS) {
            GoalsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SESSION) {
            SessionScreen(onDone = { nav.popBackStack() })
        }
    }

    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        // Start from home so Back from the session lands on home.
        nav.popBackStack(Routes.HOME, inclusive = false)
        nav.navigate(route) { launchSingleTop = true }
        onPendingRouteHandled()
    }
}
