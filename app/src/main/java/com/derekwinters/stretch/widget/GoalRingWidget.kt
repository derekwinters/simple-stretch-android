package com.derekwinters.stretch.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.derekwinters.stretch.StretchApp
import kotlinx.coroutines.launch

/** The segmented ring home screen widget (docs/spec/widgets.md). */
class GoalRingWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetUpdater.ACTION_MIDNIGHT) {
            // WIDGET-010: a new local day starts at 0.
            redrawAsync(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        redrawAsync(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // WIDGET-001: resized, so draw at the new size.
        redrawAsync(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.cancelMidnight(context)
    }

    private fun redrawAsync(context: Context) {
        val app = context.applicationContext as StretchApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                WidgetUpdater.redraw(app)
            } finally {
                pending.finish()
            }
        }
    }
}
