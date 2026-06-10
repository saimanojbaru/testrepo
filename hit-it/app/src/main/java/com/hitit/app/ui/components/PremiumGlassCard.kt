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
import com.hitit.app.ui.theme.AuroraViolet
import com.hitit.app.ui.theme.CyberGlass

/**
 * Cyber-Dreamcore glass: a smoky translucent panel over the cosmic nebula with a neon rim and a soft
 * coloured glow shadow (which reads as outer-glow on the dark canvas — real blur needs API 31). When
 * [accent] is set the rim + glow take that colour and the fill is faintly tinted; otherwise a calm
 * violet glow. Eye-friendly: the surface is dark, the neon is only on the 1px edge + glow.
 */
fun Modifier.frostedGlass(
    cornerRadius: Dp = 24.dp,
    accent: Color? = null,
    elevation: Dp = 12.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val glow = accent ?: AuroraViolet
    return this
        .shadow(elevation = elevation, shape = shape, ambientColor = glow.copy(alpha = 0.55f), spotColor = glow.copy(alpha = 0.65f))
        .clip(shape)
        .background(CyberGlass.copy(alpha = 0.72f))
        .then(if (accent != null) Modifier.background(accent.copy(alpha = 0.12f)) else Modifier)
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(glow.copy(alpha = 0.55f), glow.copy(alpha = 0.12f)),
            ),
            shape = shape,
        )
}

/** Full-width Cyber-Dreamcore glass card. When [accent] is set the whole card glows in it. */
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
