package com.hitit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hitit.app.ui.theme.AuroraInk

/**
 * Frosted-glass surface for the Aurora (light) theme: a translucent white pane that lets the aurora
 * glow through, a soft top-lit rim, and a gentle lift shadow so it floats on the canvas. Real blur
 * needs API 31; in light mode a milky translucent fill reads as frost without it. When [accent] is
 * set the pane is faintly tinted and its lift shadow takes the accent's colour.
 */
fun Modifier.frostedGlass(
    cornerRadius: Dp = 24.dp,
    accent: Color? = null,
    elevation: Dp = 10.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val glow = accent ?: AuroraInk
    return this
        .shadow(elevation = elevation, shape = shape, ambientColor = glow.copy(alpha = 0.18f), spotColor = glow.copy(alpha = 0.22f))
        .clip(shape)
        .background(Color.White.copy(alpha = 0.66f))
        .then(if (accent != null) Modifier.background(accent.copy(alpha = 0.10f)) else Modifier)
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.25f))),
            shape = shape,
        )
}

/**
 * The canonical full-width frosted card. Keeps the prior call sites working; now rendered in the
 * Aurora light style. When [accent] is set (e.g. a completed rep) the whole card lights up in it.
 */
@Composable
fun PremiumGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    contentPadding: Dp = 16.dp,
    accent: Color? = null,
    rimTint: Color = Color.White,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .frostedGlass(cornerRadius = cornerRadius, accent = accent)
            .padding(contentPadding),
        content = content,
    )
}
