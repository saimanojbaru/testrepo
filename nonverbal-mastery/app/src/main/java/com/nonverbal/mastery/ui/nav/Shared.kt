@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.nonverbal.mastery.ui.nav

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nonverbal.mastery.AppContainer
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.design.tokens.LocalReducedMotion
import com.nonverbal.mastery.design.tokens.Motion

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
val LocalAppContainer = staticCompositionLocalOf<AppContainer?> { null }
val LocalCurriculum = staticCompositionLocalOf<Curriculum?> { null }

object SharedKeys {
    fun levelCard(id: String) = "level-card-$id"
    fun levelTitle(id: String) = "level-title-$id"
    fun categoryCard(id: String) = "category-card-$id"
    fun categoryTitle(id: String) = "category-title-$id"
}

private val boundsSpec = tween<androidx.compose.ui.geometry.Rect>(Motion.DURATION_LONG, easing = Motion.Enter)

/**
 * The signature motion: a container whose bounds and tint morph between a card and a screen.
 * Off entirely when motion is reduced.
 */
@Composable
fun Modifier.sharedContainer(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavScope.current ?: return this
    if (LocalReducedMotion.current) return this
    return with(shared) {
        this@sharedContainer.sharedBounds(
            rememberSharedContentState(key),
            anim,
            enter = fadeIn(tween(Motion.DURATION, easing = Motion.Enter)),
            exit = fadeOut(tween(Motion.DURATION_SHORT, easing = Motion.Exit)),
            boundsTransform = { _, _ -> boundsSpec },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

/** A title that scales from its card position into the screen header. */
@Composable
fun Modifier.sharedTitle(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavScope.current ?: return this
    if (LocalReducedMotion.current) return this
    return with(shared) {
        this@sharedTitle.sharedBounds(
            rememberSharedContentState(key),
            anim,
            enter = fadeIn(tween(Motion.DURATION, easing = Motion.Enter)),
            exit = fadeOut(tween(Motion.DURATION_SHORT, easing = Motion.Exit)),
            boundsTransform = { _, _ -> boundsSpec },
            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.FillWidth, Alignment.CenterStart),
        )
    }
}

/** ViewModels receive the app container; no DI framework needed at this size. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = requireNotNull(LocalAppContainer.current) { "AppContainer not provided" }
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
