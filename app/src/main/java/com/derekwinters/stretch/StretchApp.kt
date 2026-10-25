package com.derekwinters.stretch

import android.app.Application
import com.derekwinters.stretch.data.AppDatabase
import com.derekwinters.stretch.data.StretchRepository
import com.derekwinters.stretch.notifications.Notifications
import com.derekwinters.stretch.scheduling.ReminderScheduler
import com.derekwinters.stretch.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock

open class StretchApp : Application() {
    /** Process-wide scope for short background work (rescheduling, receiver work). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * SCHED-016: the one clock every screen, ViewModel, the repository, the scheduler, the
     * receivers, the widget and the database migrations read the current date, time and zone
     * from. Its zone is the device's default at each call (SCHED-010 invariant). A test or
     * screenshot application can override it with `Clock.fixed(instant, zone)`.
     */
    open val clock: Clock = AppClock()

    val database: AppDatabase by lazy { AppDatabase.build(this, clock) }
    val repository: StretchRepository by lazy { StretchRepository(this, database, clock) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        // Make sure alarms reflect the stored data whenever the process starts.
        appScope.launch { ReminderScheduler.rescheduleAll(this@StretchApp) }
        // WIDGET-010: the widget may have missed a day or a wallpaper-colour change.
        WidgetUpdater.request(this)
    }
}
