package com.sortora.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortora.app.ui.MainViewModel
import com.sortora.app.ui.design.Sortora
import com.sortora.app.ui.navigation.SortoraNavHost
import com.sortora.app.ui.theme.SortoraTheme
import com.sortora.app.ui.theme.isDarkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val dark = isDarkTheme(state.themeMode)
            // Status and navigation bar icons follow the app's theme, not just the system's.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SortoraTheme(themeMode = state.themeMode, dynamicColor = state.dynamicColor) {
                Box(Modifier.fillMaxSize().background(Sortora.colors.canvas)) {
                    // Wait for settings before choosing the start screen, to avoid flashing onboarding.
                    if (!state.loading) SortoraNavHost(showOnboarding = !state.onboardingComplete)
                }
            }
        }
    }
}
