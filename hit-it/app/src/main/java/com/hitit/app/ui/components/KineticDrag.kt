package com.hitit.app.ui.components

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Composition-primitive haptics for the kinetic drag — crisp `PRIMITIVE_TICK`s as the card travels,
 * a solid `PRIMITIVE_CLICK` when it commits. Primitives are API 30+; below that (minSdk is 26) and on
 * devices without the primitive we fall back to [View.performHapticFeedback], so the gesture always
 * has *some* feedback. Requires the VIBRATE permission for the Vibrator path.
 */
object KineticHaptics {
    fun tick(view: View) =
        primitiveOrFallback(view, compositionPrimitive = VibrationEffect.Composition.PRIMITIVE_TICK, scale = 0.45f, fallback = HapticFeedbackConstants.CLOCK_TICK)

    fun commit(view: View) =
        primitiveOrFallback(view, compositionPrimitive = VibrationEffect.Composition.PRIMITIVE_CLICK, scale = 1f, fallback = HapticFeedbackConstants.LONG_PRESS)

    private fun primitiveOrFallback(view: View, compositionPrimitive: Int, scale: Float, fallback: Int) {
        if (Build.VERSION.SDK_INT >= 30) {
            val vibrator = vibrator(view.context)
            if (vibrator != null && vibrator.hasVibrator() &&
                runCatching { vibrator.arePrimitivesSupported(compositionPrimitive).firstOrNull() == true }.getOrDefault(false)
            ) {
                val ok = runCatching {
                    val effect = VibrationEffect.startComposition()
                        .addPrimitive(compositionPrimitive, scale)
                        .compose()
                    vibrator.vibrate(effect)
                }.isSuccess
                if (ok) return
            }
        }
        @Suppress("DEPRECATION")
        view.performHapticFeedback(fallback)
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}

/**
 * Elastic "drag-to-complete": pull the card to the right and it follows with rubber-band resistance
 * past the trigger point; release past the threshold to fire [onComplete] (with a committing haptic),
 * otherwise it springs back. Composition ticks step out as you drag, escalating into the commit click.
 *
 * Uses [detectHorizontalDragGestures] so it co-exists with a parent vertical scroller (it only claims
 * horizontal travel). Disable it (e.g. once the rep is met) by passing `enabled = false`.
 */
fun Modifier.kineticDragToComplete(
    enabled: Boolean = true,
    onComplete: () -> Unit,
): Modifier = if (!enabled) this else composed {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    val thresholdPx = with(density) { 96.dp.toPx() }
    val tickStepPx = with(density) { 22.dp.toPx() }
    // Tracks the last tick "notch" so each ~22dp of travel emits exactly one tick.
    val lastNotch = remember { intArrayOf(0) }
    val crossedThreshold = remember { booleanArrayOf(false) }

    this
        .graphicsLayer { translationX = offsetX.value }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = {
                    lastNotch[0] = 0
                    crossedThreshold[0] = false
                },
                onDragEnd = {
                    val committed = offsetX.value >= thresholdPx
                    if (committed) KineticHaptics.commit(view)
                    scope.launch {
                        offsetX.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                        )
                    }
                    if (committed) onComplete()
                },
                onDragCancel = {
                    scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) }
                },
            ) { _, dragAmount ->
                // Only rightward pulls count; rubber-band the travel past the threshold.
                val raw = (offsetX.value + dragAmount).coerceAtLeast(0f)
                val resisted = if (raw <= thresholdPx) raw else thresholdPx + (raw - thresholdPx) * 0.35f
                scope.launch { offsetX.snapTo(resisted) }

                // Escalating ticks: one per notch out, and a distinct tick the first time we arm.
                val notch = (resisted / tickStepPx).roundToInt()
                if (notch != lastNotch[0] && abs(resisted) < thresholdPx) {
                    lastNotch[0] = notch
                    KineticHaptics.tick(view)
                }
                if (resisted >= thresholdPx && !crossedThreshold[0]) {
                    crossedThreshold[0] = true
                    KineticHaptics.tick(view)
                }
            }
        }
}
