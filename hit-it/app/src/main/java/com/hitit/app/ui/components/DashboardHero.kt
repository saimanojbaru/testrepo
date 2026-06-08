package com.hitit.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hitit.app.ui.theme.AthleticLabelStyle
import com.hitit.app.ui.theme.HeroGradient
import com.hitit.app.ui.theme.StatNumberStyle
import com.hitit.app.ui.theme.TabularNumberStyle

/**
 * The dashboard hero: a vibrant gradient card with the big Momentum Score, tier/level, and the
 * level-progress bar. This is the "performance dashboard" focal point.
 */
@Composable
fun DashboardHero(
    score: Int,
    scoreLabel: String,
    tier: String,
    level: Int,
    levelProgress: Float,
    momentum: Long,
    flameLevel: Int,
    modifier: Modifier = Modifier,
) {
    val animatedScore by animateIntAsState(targetValue = score, animationSpec = tween(700), label = "score")
    val animatedProgress by animateFloatAsState(
        targetValue = levelProgress.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "levelProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(HeroGradient)
            .padding(20.dp),
    ) {
        LifeFlame(
            level = flameLevel,
            size = 96.dp,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // Left: today's daily Momentum score (the big, changing number).
                Column {
                    Text(
                        text = "CURRENT MOMENTUM",
                        style = AthleticLabelStyle,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(text = "$animatedScore", style = TabularNumberStyle, color = Color.White)
                        Text(
                            text = "/100",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 7.dp, start = 2.dp),
                        )
                    }
                    Text(
                        text = scoreLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
                // Right: permanent identity stats (tier/level + lifetime XP), de-cluttered.
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${tier.uppercase()} LV.$level",
                        style = AthleticLabelStyle,
                        color = Color.White,
                    )
                    OdometerText(
                        value = momentum.toInt(),
                        suffix = " ⚡",
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        color = Color.White.copy(alpha = 0.95f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .size(width = 0.dp, height = 8.dp)
                    .shimmer(color = Color.White.copy(alpha = 0.5f), durationMillis = 2200),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}

/** A compact stat tile for the quick-stats row. */
@Composable
fun StatTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = accent,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
