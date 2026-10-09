package com.derekwinters.stretch.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.derekwinters.stretch.ui.home.HomeScreen
import com.derekwinters.stretch.ui.schedule.ScheduleEditScreen
import com.derekwinters.stretch.ui.skips.SkipDaysScreen
import com.derekwinters.stretch.ui.stretches.StretchesScreen

object Routes {
    const val HOME = "home"
    const val SCHEDULE = "schedule/{scheduleId}"
    const val STRETCHES = "stretches"
    const val SKIPS = "skips"
    fun schedule(id: Long) = "schedule/$id"
}

@Composable
fun StretchNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onEditSchedule = { nav.navigate(Routes.schedule(it)) },
                onNewSchedule = { nav.navigate(Routes.schedule(-1L)) },
                onOpenStretches = { nav.navigate(Routes.STRETCHES) },
                onOpenSkips = { nav.navigate(Routes.SKIPS) },
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
    }
}
