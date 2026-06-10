package com.hitit.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import com.hitit.app.ui.theme.HitItTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var demoSeeder: DemoSeeder
    @Inject lateinit var ledgerRepository: com.hitit.app.data.repository.LedgerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Seed demo data on first launch, finalize the strict ledger for any past days, then
        // re-arm reminders (survive reboot/process death).
        lifecycleScope.launch {
            demoSeeder.seedIfNeeded()
            ledgerRepository.finalizePastDays()
            reminderScheduler.rescheduleAll()
            reminderScheduler.runSuddenDeathNow()
            reminderScheduler.scheduleSuddenDeathDaily()
            reminderScheduler.scheduleFlameCheckDaily()
        }
        // Dark cosmic theme: force light system-bar icons over the transparent bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            HitItTheme {
                // First run shows the real (seeded) app with a spotlight coachmark overlaid;
                // the gate is the existing onboardingComplete flag, flipped on finish/skip.
                var showSpotlight by remember { mutableStateOf(!appPreferences.onboardingComplete) }
                HitItApp(
                    showSpotlight = showSpotlight,
                    onSpotlightFinished = {
                        appPreferences.onboardingComplete = true
                        showSpotlight = false
                    },
                )
            }
        }
    }
}
