package com.hitit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.hitit.app.reminder.ReminderScheduler
import com.hitit.app.ui.HitItApp
import com.hitit.app.ui.theme.HitItTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Re-arm any enabled reminders so they survive reboots / process death.
        lifecycleScope.launch { reminderScheduler.rescheduleAll() }
        enableEdgeToEdge()
        setContent {
            HitItTheme {
                HitItApp()
            }
        }
    }
}
