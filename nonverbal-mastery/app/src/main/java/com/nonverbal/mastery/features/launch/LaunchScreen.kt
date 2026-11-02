package com.nonverbal.mastery.features.launch

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.nonverbal.mastery.design.components.AppMark
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Motion
import com.nonverbal.mastery.design.tokens.Space

/** The launch mark: one brass breath, then the book. At most 600ms; skipped when motion is reduced. */
@Composable
fun LaunchScreen() {
    val colors = Nv.colors
    val pulse = if (Nv.reducedMotion) 1f else {
        val t = rememberInfiniteTransition(label = "launch")
        val v by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(Motion.LAUNCH_MAX / 2, easing = Motion.Enter), RepeatMode.Reverse), label = "p")
        v
    }
    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppMark(size = 88.dp, color = colors.brass.copy(alpha = pulse))
            Spacer(Modifier.height(Space.x4))
            Text("Nonverbal Mastery", style = Nv.type.titleSmall, color = colors.ink, modifier = Modifier.alpha(0.9f))
        }
    }
}
