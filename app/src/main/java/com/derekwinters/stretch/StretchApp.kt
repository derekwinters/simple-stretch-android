package com.derekwinters.stretch

import android.app.Application
import com.derekwinters.stretch.data.AppDatabase
import com.derekwinters.stretch.data.StretchRepository
import com.derekwinters.stretch.notifications.Notifications
import com.derekwinters.stretch.scheduling.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StretchApp : Application() {
    /** Process-wide scope for short background work (rescheduling, receiver work). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: StretchRepository by lazy { StretchRepository(this, database) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        // Make sure alarms reflect the stored data whenever the process starts.
        appScope.launch { ReminderScheduler.rescheduleAll(this@StretchApp) }
    }
}
