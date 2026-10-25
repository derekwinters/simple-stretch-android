package com.derekwinters.stretch.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.derekwinters.stretch.MainActivity
import com.derekwinters.stretch.R
import com.derekwinters.stretch.StretchApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Redraws every segmented ring widget from the completion log (WIDGET-009) and re-arms the
 * midnight redraw (WIDGET-010).
 */
object WidgetUpdater {
    const val ACTION_MIDNIGHT = "com.derekwinters.stretch.widget.action.MIDNIGHT"

    private const val DEFAULT_SIDE_DP = 110
    private const val MIN_SIDE_PX = 120
    // Two bitmaps go to the launcher at every redraw; this keeps them well within RemoteViews'
    // bitmap allowance while staying sharp at 2×2 on dense screens.
    private const val MAX_SIDE_PX = 480

    private val lock = Mutex()

    /** WIDGET-010: redraw in the background, e.g. after a write in the repository. */
    fun request(context: Context) {
        val app = context.applicationContext as? StretchApp ?: return
        app.appScope.launch { redraw(app) }
    }

    /** Redraws every placed widget now; with none placed, only cancels the midnight alarm. */
    suspend fun redraw(context: Context) = lock.withLock {
        val app = context.applicationContext as StretchApp
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, GoalRingWidget::class.java))
        if (ids.isEmpty()) {
            cancelMidnight(app)
            return@withLock
        }
        // SCHED-016: today, the zone and the midnight alarm come from the app clock.
        val clock = app.clock
        val zone = clock.zone
        // WIDGET-009: read from the log every time; nothing is kept between redraws.
        val model = app.repository.goalRingOn(LocalDate.now(clock), zone)
        val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val light = WidgetPalette.select(dynamic, dark = false) { dynamicTones(app) }
        val dark = WidgetPalette.select(dynamic, dark = true) { dynamicTones(app) }
        val description = describe(app, model.centre)
        for (id in ids) {
            val side = sidePx(app, manager.getAppWidgetOptions(id))
            val views = RemoteViews(app.packageName, R.layout.widget_goal_ring).apply {
                // WIDGET-008: both drawings; the layout for the current mode shows one of them.
                setImageViewBitmap(R.id.ring_light, GoalRingRenderer.render(model, light, side))
                setImageViewBitmap(R.id.ring_dark, GoalRingRenderer.render(model, dark, side))
                setContentDescription(R.id.ring_light, description)
                setContentDescription(R.id.ring_dark, description)
                setOnClickPendingIntent(R.id.widget_root, openHome(app))
            }
            manager.updateAppWidget(id, views)
        }
        scheduleMidnight(app, GoalRing.nextRedrawAt(clock.millis(), zone))
    }

    /** WIDGET-001: the square side, from the larger of the portrait and landscape cell areas. */
    private fun sidePx(context: Context, options: Bundle?): Int {
        fun dp(key: String) = options?.getInt(key, 0) ?: 0
        val portrait = minOf(dp(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH), dp(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT))
        val landscape = minOf(dp(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH), dp(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT))
        val sideDp = maxOf(portrait, landscape).takeIf { it > 0 } ?: DEFAULT_SIDE_DP
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sideDp.toFloat(), context.resources.displayMetrics)
        return px.toInt().coerceIn(MIN_SIDE_PX, MAX_SIDE_PX)
    }

    /** WIDGET-008: the roles Material 3's dynamic schemes use, for both modes. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun dynamicTones(context: Context) = DynamicTones(
        primaryLight = context.getColor(android.R.color.system_accent1_600),
        primaryDark = context.getColor(android.R.color.system_accent1_200),
        surfaceLight = context.getColor(android.R.color.system_neutral1_10),
        surfaceDark = context.getColor(android.R.color.system_neutral1_900),
        onSurfaceLight = context.getColor(android.R.color.system_neutral1_900),
        onSurfaceDark = context.getColor(android.R.color.system_neutral1_100),
        outlineLight = context.getColor(android.R.color.system_neutral2_500),
        outlineDark = context.getColor(android.R.color.system_neutral2_400),
    )

    private fun describe(context: Context, centre: RingCentre): String = when (centre) {
        is RingCentre.Count -> context.getString(R.string.widget_goal_ring_count, centre.done, centre.total)
        RingCentre.Check -> context.getString(R.string.widget_goal_ring_all_done)
        RingCentre.None -> context.getString(R.string.widget_goal_ring_no_goals)
    }

    /** WIDGET-007: open the app on its home screen. */
    private fun openHome(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, REQUEST_OPEN_HOME, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun midnightIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_MIDNIGHT,
        Intent(context, GoalRingWidget::class.java).setAction(ACTION_MIDNIGHT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * WIDGET-010: not a wakeup alarm, as a widget only needs to be right when the screen is on; it
     * is delivered as soon as the phone wakes after midnight.
     */
    private fun scheduleMidnight(context: Context, atMillis: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = midnightIntent(context)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exact) alarms.setExact(AlarmManager.RTC, atMillis, pending) else alarms.set(AlarmManager.RTC, atMillis, pending)
        } catch (_: SecurityException) {
            alarms.set(AlarmManager.RTC, atMillis, pending)
        }
    }

    fun cancelMidnight(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(midnightIntent(context))
    }

    private const val REQUEST_OPEN_HOME = 0x5717
    private const val REQUEST_MIDNIGHT = 0x5718
}
