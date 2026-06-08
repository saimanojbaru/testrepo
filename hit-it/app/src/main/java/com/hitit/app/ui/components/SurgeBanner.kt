package com.hitit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hitit.app.ui.theme.NeonViolet
import com.hitit.app.ui.theme.TabularNumberStyle
import com.hitit.domain.surge.Surge
import com.hitit.domain.surge.SurgeEngine
import com.hitit.domain.surge.SurgeKind
import kotlinx.coroutines.delay

/**
 * The ambient high-stakes Surge banner: a glowing, pulsing card with a live MM:SS countdown. When the
 * window closes it fires [onExpired] exactly once so the coordinator can bank the consequence (a
 * debt-recovery miss doubles the day's debt). Win it by completing your target — the parent clears it.
 */
@Composable
fun SurgeBanner(
    surge: Surge,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tick `now` once a second so the countdown is live; trip the expiry exactly once.
    var nowMillis by remember(surge) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(surge) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            if (surge.isExpired(nowMillis)) {
                onExpired()
                break
            }
            delay(1000)
        }
    }

    val accent = when (surge.kind) {
        SurgeKind.DEBT_RECOVERY -> Color(0xFFFF6B3D) // urgent ember
        SurgeKind.MONOLITH_BLITZ -> NeonViolet       // hot violet
    }
    val emoji = when (surge.kind) {
        SurgeKind.DEBT_RECOVERY -> "⚡"
        SurgeKind.MONOLITH_BLITZ -> "🔥"
    }
    val remaining = surge.remainingMillis(nowMillis)
    val totalSeconds = (remaining / 1000L).toInt()
    val countdown = "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    val detail = remember(surge) { SurgeEngine.detail(surge.kind, surge.remainingMillis(surge.startedAtMillis) / 60000L) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(accent.copy(alpha = 0.12f))
            .glowingBorder(baseColor = accent, cornerRadius = 22.dp)
            .padding(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = emoji, style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = SurgeEngine.headline(surge.kind).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = accent,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Live countdown — tabular figures so the digits don't jitter as they change.
            Text(
                text = countdown,
                style = TabularNumberStyle.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
                color = Color.White,
            )
        }
    }
}
