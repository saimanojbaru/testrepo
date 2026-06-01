package com.hitit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A single coachmark: which target to spotlight + caption copy. */
data class SpotlightStep(val targetKey: String, val title: String, val body: String)

/**
 * Holds the screen-space bounds of spotlight targets (reported via [Modifier.spotlightTarget]) and
 * the current step. Hoist one instance and share it between the marked widgets and [SpotlightOverlay].
 */
class SpotlightState {
    val bounds = mutableStateMapOf<String, Rect>()
    var stepIndex by mutableIntStateOf(0)
    fun report(key: String, rect: Rect) { bounds[key] = rect }
}

@Composable
fun rememberSpotlightState(): SpotlightState = remember { SpotlightState() }

/** Report this composable's root-space bounds to [state] under [key] so the overlay can spotlight it. */
fun Modifier.spotlightTarget(key: String, state: SpotlightState): Modifier =
    this.onGloballyPositioned { state.report(key, it.boundsInRoot()) }

/**
 * Full-screen coachmark overlay. Dims everything, punches a rounded hole over the current step's
 * target (if its bounds are known), and shows a caption card with Next/Done + a Skip. Pure Compose,
 * no dependency. If a target's bounds aren't reported yet, it simply shows the dimmed screen with no
 * hole (graceful) so a missing rect can never trap the user. Always offers Skip.
 */
@Composable
fun SpotlightOverlay(
    state: SpotlightState,
    steps: List<SpotlightStep>,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) { onFinish(); return }
    val index = state.stepIndex.coerceIn(0, steps.lastIndex)
    val step = steps[index]
    val target: Rect? = state.bounds[step.targetKey]
    val scrim = Color.Black.copy(alpha = 0.78f)

    Box(modifier = modifier.fillMaxSize()) {
        // Offscreen compositing lets BlendMode.Clear punch a transparent hole in the scrim.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(scrim)
            target?.let {
                val pad = 8.dp.toPx()
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(it.left - pad, it.top - pad),
                    size = Size(it.width + pad * 2, it.height + pad * 2),
                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    blendMode = BlendMode.Clear,
                )
            }
        }

        // Caption card — anchored to the bottom so it's always on-screen regardless of the hole.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = step.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onFinish) { Text("Skip") }
                        Text(
                            text = "${index + 1} / ${steps.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = {
                            if (index >= steps.lastIndex) onFinish() else state.stepIndex = index + 1
                        }) {
                            Text(if (index >= steps.lastIndex) "Got it" else "Next")
                        }
                    }
                }
            }
        }
    }
}
