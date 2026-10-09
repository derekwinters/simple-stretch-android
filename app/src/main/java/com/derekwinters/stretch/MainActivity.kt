package com.derekwinters.stretch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.derekwinters.stretch.scheduling.ReminderScheduler
import com.derekwinters.stretch.ui.StretchNavHost
import com.derekwinters.stretch.ui.theme.StretchTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StretchTheme {
                StretchNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The exact-alarm permission may have changed while we were in Settings.
        lifecycleScope.launch { ReminderScheduler.rescheduleAll(this@MainActivity) }
    }
}
