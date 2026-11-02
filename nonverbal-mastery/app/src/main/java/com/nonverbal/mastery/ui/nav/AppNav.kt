@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.nonverbal.mastery.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.data.repo.ProgressRepository
import com.nonverbal.mastery.design.tokens.LocalReducedMotion
import com.nonverbal.mastery.design.tokens.Motion
import com.nonverbal.mastery.features.category.CategoryRouteScreen
import com.nonverbal.mastery.features.level.LevelActions
import com.nonverbal.mastery.features.level.LevelRouteScreen
import com.nonverbal.mastery.features.path.PathActions
import com.nonverbal.mastery.features.path.PathRouteScreen
import com.nonverbal.mastery.features.poselib.PoseDetailRouteScreen
import com.nonverbal.mastery.features.poselib.PoseLibraryRouteScreen
import com.nonverbal.mastery.features.progress.ProgressRouteScreen
import com.nonverbal.mastery.features.progress.ReflectionsRouteScreen
import com.nonverbal.mastery.features.review.ReviewRouteScreen
import com.nonverbal.mastery.features.settings.CrisisScreen
import com.nonverbal.mastery.features.settings.EthicsScreen
import com.nonverbal.mastery.features.settings.JournalExport
import com.nonverbal.mastery.features.settings.SettingsRouteScreen
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContentScope
import androidx.navigation.NavBackStackEntry

/** Opening a level goes through its safety gate first, if it has one. */
fun NavHostController.openLevel(curriculum: Curriculum, levelId: String, section: Int = 0) {
    val level = curriculum.levels[levelId]
    if (level?.safetyGate != null) navigate(CrisisRoute(thenLevelId = levelId)) else navigate(LevelRoute(levelId, section))
}

@Composable
fun AppNav(curriculum: Curriculum, nav: NavHostController = rememberNavController()) {
    val reduced = LocalReducedMotion.current
    val enter: EnterTransition = if (reduced) EnterTransition.None else
        fadeIn(tween(Motion.DURATION, easing = Motion.Enter)) + slideInVertically(tween(Motion.DURATION, easing = Motion.Enter)) { it / 40 }
    val exit: ExitTransition = if (reduced) ExitTransition.None else fadeOut(tween(Motion.DURATION_SHORT, easing = Motion.Exit))
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val export: () -> Unit = {
        if (container != null) scope.launch {
            JournalExport.share(context, ProgressRepository.encode(container.progress.exportJournal(curriculum.pack.version)))
        }
    }
    val back: () -> Unit = { nav.popBackStack() }
    val home: () -> Unit = { nav.popBackStack(PathRoute, inclusive = false) }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
            NavHost(
                navController = nav,
                startDestination = PathRoute,
                enterTransition = { enter },
                exitTransition = { exit },
                popEnterTransition = { if (reduced) EnterTransition.None else fadeIn(tween(Motion.DURATION, easing = Motion.Enter)) },
                popExitTransition = { exit },
            ) {
                screen<PathRoute> {
                    PathRouteScreen(
                        curriculum,
                        PathActions(
                            openCategory = { nav.navigate(CategoryRoute(it)) },
                            openLevel = { nav.openLevel(curriculum, it) },
                            openReviews = { nav.navigate(ReviewsRoute) },
                            openPoseLibrary = { nav.navigate(PoseLibraryRoute) },
                            openProgress = { nav.navigate(ProgressRoute) },
                            openSettings = { nav.navigate(SettingsRoute) },
                            openEthics = { nav.navigate(EthicsRoute) },
                            openCrisis = { nav.navigate(CrisisRoute()) },
                        ),
                    )
                }
                screen<CategoryRoute> { entry ->
                    val route = entry.toRoute<CategoryRoute>()
                    CategoryRouteScreen(curriculum, route.categoryId, back, { nav.openLevel(curriculum, it) }, { nav.navigate(CrisisRoute()) })
                }
                screen<LevelRoute> { entry ->
                    val route = entry.toRoute<LevelRoute>()
                    LevelRouteScreen(
                        curriculum, route.levelId, route.section,
                        LevelActions(
                            onBack = back,
                            onPose = { id, other -> nav.navigate(PoseDetailRoute(id, other)) },
                            onEthics = { nav.navigate(EthicsRoute) },
                            onPoseLibrary = { nav.navigate(PoseLibraryRoute) },
                            onSettings = { nav.navigate(SettingsRoute) },
                            onOpenLevel = { id ->
                                nav.popBackStack()
                                nav.openLevel(curriculum, id)
                            },
                        ),
                    )
                }
                screen<PoseLibraryRoute> {
                    PoseLibraryRouteScreen(curriculum, back) { nav.navigate(PoseDetailRoute(it)) }
                }
                screen<PoseDetailRoute> { entry ->
                    val route = entry.toRoute<PoseDetailRoute>()
                    PoseDetailRouteScreen(curriculum, route.poseId, route.compareWith, back) { nav.openLevel(curriculum, it) }
                }
                screen<ReviewsRoute> { ReviewRouteScreen(curriculum, back) { nav.openLevel(curriculum, it) } }
                screen<ProgressRoute> {
                    ProgressRouteScreen(
                        curriculum, back,
                        onOpenLevel = { nav.openLevel(curriculum, it) },
                        onReflections = { nav.navigate(ReflectionsRoute) },
                        onCategory = { nav.navigate(CategoryRoute(it)) },
                    )
                }
                screen<ReflectionsRoute> {
                    ReflectionsRouteScreen(curriculum, back, { id, section -> nav.openLevel(curriculum, id, section) }, export)
                }
                screen<SettingsRoute> {
                    SettingsRouteScreen(
                        curriculum, back,
                        onEthics = { nav.navigate(EthicsRoute) },
                        onCrisis = { nav.navigate(CrisisRoute()) },
                        onReflections = { nav.navigate(ReflectionsRoute) },
                    )
                }
                screen<EthicsRoute> { EthicsScreen(curriculum.pack.ethics, back) }
                screen<CrisisRoute> { entry ->
                    val route = entry.toRoute<CrisisRoute>()
                    val level = route.thenLevelId?.let { curriculum.levels[it] }
                    CrisisScreen(
                        gate = level?.safetyGate ?: curriculum.pack.crisisResources,
                        continueLabel = level?.let { "Continue to ${curriculum.reference(it)}  ${it.title}" },
                        onContinue = level?.let {
                            {
                                nav.popBackStack()
                                nav.navigate(LevelRoute(it.id))
                            }
                        },
                        onHome = home,
                        onBack = back,
                    )
                }
            }
        }
    }
}

/** A destination that exposes its AnimatedVisibilityScope for shared transitions. */
private inline fun <reified T : Any> androidx.navigation.NavGraphBuilder.screen(
    crossinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        CompositionLocalProvider(LocalNavScope provides this) { content(entry) }
    }
}
