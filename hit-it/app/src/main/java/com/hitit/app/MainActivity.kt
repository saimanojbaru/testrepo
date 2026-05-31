package com.hitit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.hitit.app.data.DemoSeeder
import com.hitit.app.data.local.AppPreferences
import com.hitit.app.reminder.ReminderScheduler
import com.hitit.app.ui.HitItApp
import com.hitit.app.ui.screens.onboarding.OnboardingScreen
import com.hitit.app.ui.theme.HitItTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var demoSeeder: DemoSeeder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Seed demo data on first launch, then re-arm reminders (survive reboot/process death).
        lifecycleScope.launch {
            demoSeeder.seedIfNeeded()
            reminderScheduler.rescheduleAll()
        }
        enableEdgeToEdge()
        setContent {
            HitItTheme {
                var onboarded by remember { mutableStateOf(appPreferences.onboardingComplete) }
                if (onboarded) {
                    HitItApp()
                } else {
                    OnboardingScreen(onFinish = { onboarded = true })
                }
            }
        }
    }
}
