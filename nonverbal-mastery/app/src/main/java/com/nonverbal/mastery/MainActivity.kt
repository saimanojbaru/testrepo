package com.nonverbal.mastery

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nonverbal.mastery.data.repo.ContentState
import com.nonverbal.mastery.data.repo.ThemeMode
import com.nonverbal.mastery.design.components.ErrorState
import com.nonverbal.mastery.design.components.LoadingState
import com.nonverbal.mastery.design.components.LocalPoseLibrary
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.theme.NonverbalTheme
import com.nonverbal.mastery.design.tokens.DefaultReadingSize
import com.nonverbal.mastery.design.tokens.Motion
import com.nonverbal.mastery.features.launch.LaunchScreen
import com.nonverbal.mastery.ui.nav.AppNav
import com.nonverbal.mastery.ui.nav.LocalAppContainer
import com.nonverbal.mastery.ui.nav.LocalCurriculum
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as MasteryApp).container
        setContent {
            val vm: MasteryViewModel = viewModel(factory = viewModelFactory { initializer { MasteryViewModel(container) } })
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            val content by vm.content.collectAsStateWithLifecycle()
            val dark = when (prefs?.theme) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            val reduced = vm.reducedMotion(prefs)
            NonverbalTheme(darkTheme = dark, reducedMotion = reduced, readingSize = prefs?.readingSize ?: DefaultReadingSize) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    Root(content, reduced)
                }
            }
        }
    }
}

@Composable
private fun Root(content: ContentState, reduced: Boolean) {
    // The launch mark shows for at most 600ms, and not at all when motion is reduced.
    var launching by remember { mutableStateOf(!reduced) }
    LaunchedEffect(Unit) { if (launching) { delay(Motion.LAUNCH_MAX.toLong()); launching = false } }
    val phase = when {
        launching -> "launch"
        content is ContentState.Ready -> "ready"
        content is ContentState.Failed -> "failed"
        else -> "loading"
    }
    Box(Modifier.fillMaxSize().background(Nv.colors.background)) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                if (reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else fadeIn(tween(Motion.DURATION, easing = Motion.Enter)) togetherWith fadeOut(tween(Motion.DURATION_SHORT, easing = Motion.Exit))
            },
            label = "root",
        ) { p ->
            when (p) {
                "launch" -> LaunchScreen()
                "loading" -> LoadingState()
                "failed" -> ErrorState()
                else -> {
                    val curriculum = (content as? ContentState.Ready)?.curriculum
                    if (curriculum != null) {
                        CompositionLocalProvider(
                            LocalCurriculum provides curriculum,
                            LocalPoseLibrary provides curriculum.poses,
                        ) { AppNav(curriculum) }
                    }
                }
            }
        }
    }
}
