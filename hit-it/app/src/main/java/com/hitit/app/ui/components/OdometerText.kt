package com.hitit.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * A number that rolls up to its new value (odometer / slot-machine effect) instead of snapping —
 * the satisfying "your XP just jumped" feel. Backed by [animateIntAsState].
 */
@Composable
fun OdometerText(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    suffix: String = "",
    durationMillis: Int = 800,
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        label = "odometer",
    )
    Text(
        text = "${formatThousands(animated)}$suffix",
        modifier = modifier,
        style = style,
        color = color,
    )
}

/** Group digits with commas (e.g. 1002 -> "1,002") without pulling in a locale formatter. */
private fun formatThousands(n: Int): String {
    val neg = n < 0
    val s = kotlin.math.abs(n).toString()
    val sb = StringBuilder()
    for ((i, c) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return if (neg) "-$sb" else sb.toString()
}
