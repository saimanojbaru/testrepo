package com.hitit.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hitit.domain.glitch.RealityGlitch
import kotlin.math.floor
import kotlin.random.Random

/** Global easter-egg switch: triple-tap the dashboard flame to shift the whole cosmos. */
object VibeShiftState {
    val active = mutableStateOf(false)
    fun toggle() { active.value = !active.value }
}

/**
 * Reality Glitch — at extreme momentum (>= [RealityGlitch.THRESHOLD]) reality starts artifacting
 * with praise. Every ~9s a ~450ms burst fires: RGB-split slivers + scanlines skitter across the
 * screen and a deterministic praise line flashes. Pure overlay (no input interception), draw-phase
 * animation only, and completely absent below the threshold.
 */
@Composable
fun RealityGlitchOverlay(momentumScore: Int, modifier: Modifier = Modifier) {
    if (!RealityGlitch.isEligible(momentumScore)) return

    val cycles = RealityGlitch.praiseCount
    val transition = rememberInfiniteTransition(label = "glitch")
    // One full sweep = praiseCount cycles of ~9s each; seed = which cycle we're in.
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = cycles.toFloat(),
        animationSpec = infiniteRepeatable(tween(cycles * 9000, easing = LinearEasing), RepeatMode.Restart),
        label = "glitchPhase",
    )
    val seed = floor(t).toInt()
    val phase = t - seed            // 0..1 within the current cycle
    val burst = phase < 0.05f       // ~450ms artifact window per cycle

    // Pre-baked sliver geometry, re-rolled each burst via the seed.
    val slivers = remember(seed) {
        val rng = Random(seed)
        List(7) {
            Triple(rng.nextFloat(), 0.004f + rng.nextFloat() * 0.012f, rng.nextFloat() * 0.04f - 0.02f)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (burst) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val w = size.width
                        val h = size.height
                        slivers.forEach { (fy, fh, dx) ->
                            val y = fy * h
                            val sh = fh * h
                            // RGB-split: cyan sliver one way, magenta the other.
                            drawRect(
                                color = Color(0xFF2DE2E2).copy(alpha = 0.28f),
                                topLeft = Offset(dx * w, y),
                                size = Size(w, sh),
                            )
                            drawRect(
                                color = Color(0xFFFF2E97).copy(alpha = 0.22f),
                                topLeft = Offset(-dx * w, y + sh * 0.6f),
                                size = Size(w, sh * 0.7f),
                            )
                        }
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC0C0A1F)),
            ) {
                Text(
                    text = RealityGlitch.praise(seed),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF7CFF4F),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
