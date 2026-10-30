@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.sortora.app.ui.design

import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Real blur needs RenderEffect (Android 12+); the JVM test runtime has no renderer for it. */
val GlassBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.FINGERPRINT != "robolectric"

/**
 * Page frame shared by every screen: warm canvas, optional ambient light at the top, content
 * that glass bars can blur, and Sortora's snackbar.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbar: SnackbarHostState? = null,
    ambient: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = Sortora.colors
    val haze = rememberHazeState(blurEnabled = GlassBlurSupported)
    CompositionLocalProvider(LocalHazeState provides haze) {
        Scaffold(
            modifier = modifier,
            topBar = topBar,
            bottomBar = bottomBar,
            snackbarHost = { if (snackbar != null) SortoraSnackbarHost(snackbar) },
            containerColor = c.canvas,
            contentColor = c.ink,
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(haze)
                    .then(if (ambient) Modifier.ambientLight(c) else Modifier),
            ) { content(padding) }
        }
    }
}

/** A faint warm glow at the top of the page, like light on paper. */
fun Modifier.ambientLight(c: SortoraColors): Modifier = drawBehind {
    val r = size.width * 0.95f
    drawCircle(
        Brush.radialGradient(listOf(c.accent.copy(alpha = if (c.dark) 0.10f else 0.07f), Color.Transparent), Offset(size.width * 0.85f, -r * 0.2f), r),
        r, Offset(size.width * 0.85f, -r * 0.2f),
    )
}

/** True once the page has scrolled past its large title. */
@Composable
fun LazyListState.scrolledPast(threshold: Int = 1): State<Boolean> = remember(this) {
    derivedStateOf { firstVisibleItemIndex >= threshold }
}

@Composable
fun LazyListState.isScrolled(): State<Boolean> = remember(this) {
    derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 4 }
}

@Composable
fun ScrollState.scrolledPast(px: Float): State<Boolean> = remember(this, px) { derivedStateOf { value > px } }

@Composable
fun titleScrollThreshold(): Float = with(LocalDensity.current) { 72.dp.toPx() }

/**
 * Morphs this element into the element with the same [key] on the next screen (or in an overlay
 * driven by [scope]). Falls back to nothing when there's no transition to join.
 */
@Composable
fun Modifier.morph(key: Any, scope: AnimatedVisibilityScope? = LocalNavScope.current): Modifier {
    val shared = LocalSharedScope.current ?: return this
    if (scope == null) return this
    return with(shared) {
        this@morph.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = scope,
            enter = fadeIn(Motion.fade(240)),
            exit = fadeOut(Motion.fade(180)),
            boundsTransform = MorphBounds,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

/** Shared-element variant for things that look the same on both ends (a thumbnail, a glyph). */
@Composable
fun Modifier.sharedThing(key: Any, scope: AnimatedVisibilityScope? = LocalNavScope.current): Modifier {
    val shared = LocalSharedScope.current ?: return this
    if (scope == null) return this
    return with(shared) {
        this@sharedThing.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = scope,
            boundsTransform = MorphBounds,
        )
    }
}

val MorphBounds = BoundsTransform { _, _ -> spring(dampingRatio = 0.84f, stiffness = 320f) }
