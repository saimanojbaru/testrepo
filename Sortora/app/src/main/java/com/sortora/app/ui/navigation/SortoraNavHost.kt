package com.sortora.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sortora.app.ui.analysis.AnalysisScreen
import com.sortora.app.ui.apply.ApplyScreen
import com.sortora.app.ui.design.LocalNavScope
import com.sortora.app.ui.design.LocalSharedScope
import com.sortora.app.ui.design.Motion
import com.sortora.app.ui.faces.FaceGroupsScreen
import com.sortora.app.ui.history.HistoryScreen
import com.sortora.app.ui.home.HomeScreen
import com.sortora.app.ui.onboarding.OnboardingScreen
import com.sortora.app.ui.preview.PreviewScreen
import com.sortora.app.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object OnboardingRoute
@Serializable object HomeRoute
@Serializable data class AnalysisRoute(val sessionId: Long)
@Serializable data class PreviewRoute(val sessionId: Long)
@Serializable data class ApplyRoute(val sessionId: Long)
@Serializable data class FaceGroupsRoute(val sessionId: Long)
@Serializable object HistoryRoute
@Serializable object SettingsRoute

// Pages glide a short distance and fade, on springs, rather than sliding a full screen width:
// it reads as one calm space, and predictive back scrubs through the same motion.
private val slideSpring = spring<IntOffset>(dampingRatio = 1f, stiffness = 380f)

private val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(320, delayMillis = 40, easing = Motion.EaseOutQuint)) +
        slideInHorizontally(slideSpring) { it / 9 } +
        scaleIn(spring(dampingRatio = 1f, stiffness = 380f), initialScale = 0.985f)
}
private val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(200, easing = Motion.EaseOutQuint)) + slideOutHorizontally(slideSpring) { -it / 22 }
}
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(300, delayMillis = 30, easing = Motion.EaseOutQuint)) + slideInHorizontally(slideSpring) { -it / 22 }
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(200, easing = Motion.EaseOutQuint)) +
        slideOutHorizontally(slideSpring) { it / 9 } +
        scaleOut(spring(dampingRatio = 1f, stiffness = 380f), targetScale = 0.985f)
}

/** Registers a destination and hands its animation scope to shared-element modifiers. */
private inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T> { entry -> CompositionLocalProvider(LocalNavScope provides this) { content(entry) } }
}

@Composable
fun SortoraNavHost(showOnboarding: Boolean, navController: NavHostController = rememberNavController()) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
            NavHost(
                navController = navController,
                startDestination = if (showOnboarding) OnboardingRoute else HomeRoute,
                enterTransition = enter,
                exitTransition = exit,
                popEnterTransition = popEnter,
                popExitTransition = popExit,
            ) {
                screen<OnboardingRoute> {
                    OnboardingScreen(
                        onFinished = {
                            // Clear everything (onboarding can be replayed from Settings) and land on Home.
                            navController.navigate(HomeRoute) { popUpTo(navController.graph.id) { inclusive = true } }
                        },
                    )
                }
                screen<HomeRoute> {
                    HomeScreen(
                        onOpenSession = { session -> navController.navigate(AnalysisRoute(session)) },
                        onOpenPreview = { session -> navController.navigate(PreviewRoute(session)) },
                        onOpenApply = { session -> navController.navigate(ApplyRoute(session)) },
                        onOpenHistory = { navController.navigate(HistoryRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                    )
                }
                screen<AnalysisRoute> {
                    AnalysisScreen(
                        onBack = { navController.popBackStack() },
                        onReview = { session ->
                            navController.navigate(PreviewRoute(session)) { popUpTo(HomeRoute) }
                        },
                    )
                }
                screen<PreviewRoute> {
                    PreviewScreen(
                        onBack = { navController.popBackStack() },
                        onApplied = { session ->
                            navController.navigate(ApplyRoute(session)) { popUpTo(HomeRoute) }
                        },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenFaces = { session -> navController.navigate(FaceGroupsRoute(session)) },
                    )
                }
                screen<FaceGroupsRoute> {
                    FaceGroupsScreen(onBack = { navController.popBackStack() })
                }
                screen<ApplyRoute> {
                    ApplyScreen(
                        onDone = { navController.popBackStack(HomeRoute, inclusive = false) },
                        onOpenHistory = {
                            navController.navigate(HistoryRoute) { popUpTo(HomeRoute) }
                        },
                    )
                }
                screen<HistoryRoute> {
                    HistoryScreen(onBack = { navController.popBackStack() })
                }
                screen<SettingsRoute> {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onReplayOnboarding = { navController.navigate(OnboardingRoute) },
                    )
                }
            }
        }
    }
}
