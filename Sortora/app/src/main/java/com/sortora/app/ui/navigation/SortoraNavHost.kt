package com.sortora.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sortora.app.ui.analysis.AnalysisScreen
import com.sortora.app.ui.apply.ApplyScreen
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

@Composable
fun SortoraNavHost(showOnboarding: Boolean, navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = if (showOnboarding) OnboardingRoute else HomeRoute) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onFinished = {
                    // Clear everything (onboarding can be replayed from Settings) and land on Home.
                    navController.navigate(HomeRoute) { popUpTo(navController.graph.id) { inclusive = true } }
                },
            )
        }
        composable<HomeRoute> {
            HomeScreen(
                onOpenSession = { session -> navController.navigate(AnalysisRoute(session)) },
                onOpenPreview = { session -> navController.navigate(PreviewRoute(session)) },
                onOpenApply = { session -> navController.navigate(ApplyRoute(session)) },
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<AnalysisRoute> {
            AnalysisScreen(
                onBack = { navController.popBackStack() },
                onReview = { session ->
                    navController.navigate(PreviewRoute(session)) { popUpTo(HomeRoute) }
                },
            )
        }
        composable<PreviewRoute> {
            PreviewScreen(
                onBack = { navController.popBackStack() },
                onApplied = { session ->
                    navController.navigate(ApplyRoute(session)) { popUpTo(HomeRoute) }
                },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenFaces = { session -> navController.navigate(FaceGroupsRoute(session)) },
            )
        }
        composable<FaceGroupsRoute> {
            FaceGroupsScreen(onBack = { navController.popBackStack() })
        }
        composable<ApplyRoute> {
            ApplyScreen(
                onDone = { navController.popBackStack(HomeRoute, inclusive = false) },
                onOpenHistory = {
                    navController.navigate(HistoryRoute) { popUpTo(HomeRoute) }
                },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onReplayOnboarding = { navController.navigate(OnboardingRoute) },
            )
        }
    }
}
