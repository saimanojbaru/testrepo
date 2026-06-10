package com.hitit.app.ui.screens.today

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.ui.components.ConfettiOverlay
import com.hitit.app.ui.components.LifeFlame
import com.hitit.app.ui.components.MomentumSparkline
import com.hitit.app.ui.components.OdometerText
import com.hitit.app.ui.components.SurgeBanner
import com.hitit.app.ui.components.frostedGlass
import com.hitit.app.ui.components.shimmer
import com.hitit.app.ui.theme.AthleticLabelStyle
import com.hitit.app.ui.theme.AuroraAmber
import com.hitit.app.ui.theme.AuroraCyan
import com.hitit.app.ui.theme.AuroraInk
import com.hitit.app.ui.theme.AuroraMist
import com.hitit.app.ui.theme.AuroraMuted
import com.hitit.app.ui.theme.AuroraPink
import com.hitit.app.ui.theme.AuroraViolet
import com.hitit.app.ui.theme.HeroGradient
import com.hitit.app.ui.theme.parseHexColor

@Composable
fun TodayScreen(
    onAddRep: () -> Unit,
    onOpenRep: (Long) -> Unit,
    onLockIn: () -> Unit,
    onCheckIn: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val celebration by viewModel.celebration.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(celebration) {
        celebration?.let {
            haptics.performHapticFeedback(
                if (it.perfectDay) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
            )
            if (Build.VERSION.SDK_INT >= 33 && viewModel.shouldAskNotificationPermission()) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AuroraTopBar(dateLabel = state.dateLabel, onLockIn = onLockIn, onAddRep = onAddRep)

            AuroraHero(
                score = state.momentumScore,
                scoreLabel = state.momentumLabel,
                tier = state.tier,
                level = state.level,
                levelProgress = state.progress,
                lifetimeMomentum = state.momentum,
                flameLevel = state.flameLevel,
            )

            state.surge?.let { surge ->
                SurgeBanner(surge = surge, onExpired = { viewModel.onSurgeExpired() })
            }

            StatStrip(
                reps = "${state.repsDone}/${state.repsTotal}",
                hits = "${state.hitsDoneToday}",
                focus = "${state.focusMinutesToday}m",
                mood = state.checkInMood?.let { "$it" } ?: "—",
            )

            // Reps as a horizontal swipe rail — the dashboard's core, no endless vertical stack.
            RailHeader(title = "Today's reps", trailing = "${state.repsDone}/${state.repsTotal} done")
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.reps, key = { it.id }) { rep ->
                    RepTile(
                        rep = rep,
                        onToggle = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggle(rep)
                        },
                        onOpen = { onOpenRep(rep.id) },
                    )
                }
                item { AddRepTile(onClick = onAddRep) }
            }

            // Check-in + Main target as two compact frosted pills (or check-in full width).
            TodayDuo(
                checkedIn = state.checkedIn,
                mood = state.checkInMood,
                onCheckIn = onCheckIn,
                mainTarget = state.mainTarget,
                onToggleTarget = { state.mainTarget?.let { viewModel.toggleMainTarget(it) } },
            )

            if (state.last7Intensity.isNotEmpty()) {
                RailHeader(title = "Last 7 days", trailing = "")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .frostedGlass(cornerRadius = 22.dp)
                        .padding(16.dp),
                ) {
                    MomentumSparkline(intensities = state.last7Intensity, modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        ConfettiOverlay(trigger = celebration?.seq?.plus(1) ?: 0L)
        LaunchedEffect(celebration) { if (celebration != null) viewModel.consumeCelebration() }
    }
}

@Composable
private fun AuroraTopBar(dateLabel: String, onLockIn: () -> Unit, onAddRep: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 8.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Hit it",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = AuroraInk,
            )
            Text(text = dateLabel, style = MaterialTheme.typography.bodyMedium, color = AuroraMuted)
        }
        Row {
            IconButton(onClick = onLockIn) {
                Icon(Icons.Filled.Timer, contentDescription = "Lock In", tint = AuroraViolet)
            }
            IconButton(onClick = onAddRep) {
                Icon(Icons.Filled.Add, contentDescription = "Add Rep", tint = AuroraViolet)
            }
        }
    }
}

/** Borderless hero floating on the aurora: huge gradient momentum number + glowing flame + progress. */
@Composable
private fun AuroraHero(
    score: Int,
    scoreLabel: String,
    tier: String,
    level: Int,
    levelProgress: Float,
    lifetimeMomentum: Long,
    flameLevel: Int,
) {
    val animatedScore by animateIntAsState(targetValue = score, animationSpec = tween(700), label = "score")
    val animatedProgress by animateFloatAsState(
        targetValue = levelProgress.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "progress",
    )
    Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "MOMENTUM TODAY", style = AthleticLabelStyle, color = AuroraMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$animatedScore",
                        style = TextStyle(
                            brush = HeroGradient,
                            fontSize = 76.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-2).sp,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                    Text(
                        text = "/100",
                        style = MaterialTheme.typography.titleMedium,
                        color = AuroraMuted,
                        modifier = Modifier.padding(bottom = 14.dp, start = 2.dp),
                    )
                }
                Text(
                    text = scoreLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = AuroraViolet,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LifeFlame(level = flameLevel, size = 96.dp)
                Text(
                    text = "${tier.uppercase()} · LV.$level",
                    style = AthleticLabelStyle,
                    color = AuroraInk,
                )
                OdometerText(
                    value = lifetimeMomentum.toInt(),
                    suffix = " ⚡",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    color = AuroraMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("LV $level", style = AthleticLabelStyle, color = AuroraMuted)
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmer(color = Color.White.copy(alpha = 0.7f), durationMillis = 2200),
                color = AuroraViolet,
                trackColor = AuroraMist,
            )
            Text("LV ${level + 1}", style = AthleticLabelStyle, color = AuroraMuted)
        }
    }
}

@Composable
private fun StatStrip(reps: String, hits: String, focus: String, mood: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .frostedGlass(cornerRadius = 24.dp)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell("Reps", reps, AuroraViolet, Modifier.weight(1f))
        StatDivider()
        StatCell("Hits", hits, AuroraCyan, Modifier.weight(1f))
        StatDivider()
        StatCell("Focus", focus, AuroraPink, Modifier.weight(1f))
        StatDivider()
        StatCell("Mood", mood, AuroraAmber, Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = accent,
        )
        Text(text = label.uppercase(), style = AthleticLabelStyle, color = AuroraMuted)
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .height(34.dp)
            .width(1.dp)
            .background(AuroraMist),
    )
}

@Composable
private fun RailHeader(title: String, trailing: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AuroraInk)
        if (trailing.isNotEmpty()) {
            Text(text = trailing, style = MaterialTheme.typography.labelMedium, color = AuroraMuted)
        }
    }
}

@Composable
private fun RepTile(rep: TodayRepUi, onToggle: () -> Unit, onOpen: () -> Unit) {
    val accent = parseHexColor(rep.colorHex, AuroraViolet)
    Column(
        modifier = Modifier
            .width(156.dp)
            .height(166.dp)
            .frostedGlass(cornerRadius = 24.dp, accent = if (rep.met) accent else null)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = rep.emoji, style = MaterialTheme.typography.titleMedium)
            }
            if (rep.streak > 0) {
                Text(
                    text = "🔥${rep.streak}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
        }
        Text(
            text = rep.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = AuroraInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = rep.progressText, style = MaterialTheme.typography.labelSmall, color = AuroraMuted)
            CompletionRing(met = rep.met, accent = accent, onToggle = onToggle)
        }
    }
}

@Composable
private fun CompletionRing(met: Boolean, accent: Color, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "press",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (met) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "check",
    )
    Box(
        modifier = Modifier
            .scale(pressScale)
            .size(40.dp)
            .clip(CircleShape)
            .background(if (met) accent else accent.copy(alpha = 0.12f))
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checkScale > 0.01f) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Done",
                tint = Color.White,
                modifier = Modifier.scale(checkScale),
            )
        }
    }
}

@Composable
private fun AddRepTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(156.dp)
            .height(166.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(AuroraViolet.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add rep", tint = AuroraViolet)
        }
        Spacer(Modifier.height(8.dp))
        Text("New rep", style = MaterialTheme.typography.labelLarge, color = AuroraMuted)
    }
}

@Composable
private fun TodayDuo(
    checkedIn: Boolean,
    mood: Int?,
    onCheckIn: () -> Unit,
    mainTarget: MainTargetUi?,
    onToggleTarget: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CheckInPill(checkedIn = checkedIn, mood = mood, onClick = onCheckIn, modifier = Modifier.weight(1f))
        if (mainTarget != null) {
            TargetPill(target = mainTarget, onToggle = onToggleTarget, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CheckInPill(checkedIn: Boolean, mood: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(96.dp)
            .frostedGlass(cornerRadius = 22.dp, accent = if (checkedIn) AuroraAmber else null)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = if (checkedIn) moodEmoji(mood ?: 5) else "📝", style = MaterialTheme.typography.headlineSmall)
        Column {
            Text(
                text = if (checkedIn) "Checked in" else "Check in",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AuroraInk,
            )
            Text(
                text = if (checkedIn) "Tap to update" else "How are you?",
                style = MaterialTheme.typography.labelSmall,
                color = AuroraMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TargetPill(target: MainTargetUi, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(96.dp)
            .frostedGlass(cornerRadius = 22.dp, accent = if (target.done) AuroraViolet else null)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = AuroraViolet, modifier = Modifier.size(16.dp))
                Text("MAIN TARGET", style = AthleticLabelStyle, color = AuroraMuted)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = target.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AuroraInk,
                textDecoration = if (target.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CompletionRing(met = target.done, accent = AuroraViolet, onToggle = onToggle)
    }
}

private fun moodEmoji(mood: Int): String = when {
    mood <= 2 -> "😞"
    mood <= 4 -> "😕"
    mood <= 6 -> "😐"
    mood <= 8 -> "🙂"
    else -> "😄"
}
