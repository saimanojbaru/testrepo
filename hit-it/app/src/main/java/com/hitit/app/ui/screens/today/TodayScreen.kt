package com.hitit.app.ui.screens.today

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.hitit.app.ui.components.RealityGlitchOverlay
import com.hitit.app.ui.components.SegmentedGauge
import com.hitit.app.ui.components.SurgeBanner
import com.hitit.app.ui.components.VibeShiftState
import com.hitit.app.ui.components.frostedGlass
import com.hitit.app.ui.theme.AthleticLabelStyle
import com.hitit.app.ui.theme.AuroraAmber
import com.hitit.app.ui.theme.AuroraCyan
import com.hitit.app.ui.theme.AuroraInk
import com.hitit.app.ui.theme.AuroraMist
import com.hitit.app.ui.theme.AuroraMuted
import com.hitit.app.ui.theme.AuroraPink
import com.hitit.app.ui.theme.AuroraViolet
import com.hitit.app.ui.theme.ToxicGreen
import com.hitit.app.ui.theme.parseHexColor
import com.hitit.domain.flame.LifeFlame as FlameModel

/**
 * Home as a bento dashboard (per the reference video): a dense mosaic of compact glass tiles —
 * momentum ring, flame, tier, reps, hits, body, money, focus, mood, 7-day chart — everything at a
 * glance, every tile a door into its pillar. The reps rail below keeps one-tap completion.
 */
@Composable
fun TodayScreen(
    onAddRep: () -> Unit,
    onOpenRep: (Long) -> Unit,
    onLockIn: () -> Unit,
    onCheckIn: () -> Unit,
    onSeeAllReps: () -> Unit = {},
    onSeeAllHits: () -> Unit = {},
    onOpenBody: () -> Unit = {},
    onOpenMoney: () -> Unit = {},
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

            state.surge?.let { surge ->
                SurgeBanner(surge = surge, onExpired = { viewModel.onSurgeExpired() })
            }

            // ── The bento grid ────────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MomentumRingTile(
                        score = state.momentumScore,
                        label = state.momentumLabel,
                        levelProgress = state.progress,
                        level = state.level,
                        debt = state.outstandingDebt,
                        modifier = Modifier
                            .weight(1f)
                            .height(196.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FlameTile(
                            flameLevel = state.flameLevel,
                            streak = state.bestStreakToday,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(93.dp),
                        )
                        TierTile(
                            tier = state.tier,
                            level = state.level,
                            momentum = state.momentum,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(93.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = "REPS",
                        value = "${state.repsDone}/${state.repsTotal}",
                        accent = AuroraViolet,
                        onClick = onSeeAllReps,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.repsTotal > 0) {
                            SegmentedGauge(
                                completed = state.repsDone,
                                total = state.repsTotal,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    StatTile(
                        label = "HITS",
                        value = "${state.hitsDoneToday}",
                        accent = AuroraCyan,
                        caption = "crushed today",
                        onClick = onSeeAllHits,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = "BODY",
                        value = state.steps?.let { compact(it) } ?: "—",
                        accent = ToxicGreen,
                        caption = if (state.kcalToday > 0) "👟 steps · ${state.kcalToday} kcal" else "👟 steps · log fuel",
                        onClick = onOpenBody,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "MONEY",
                        value = "₹${state.weekSpendRupees}",
                        accent = AuroraAmber,
                        caption = "this week",
                        onClick = onOpenMoney,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = "FOCUS",
                        value = "${state.focusMinutesToday}m",
                        accent = AuroraPink,
                        caption = "lock in →",
                        onClick = onLockIn,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "MOOD",
                        value = if (state.checkedIn) moodEmoji(state.checkInMood ?: 5) else "📝",
                        accent = AuroraAmber,
                        caption = if (state.checkedIn) "vibe checked" else "check in →",
                        onClick = onCheckIn,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.last7Intensity.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .frostedGlass(cornerRadius = 20.dp)
                            .padding(12.dp),
                    ) {
                        Text(text = "LAST 7 DAYS", style = AthleticLabelStyle, color = AuroraMuted)
                        Spacer(Modifier.height(8.dp))
                        MomentumSparkline(
                            intensities = state.last7Intensity,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ── Reps rail: the one-tap completion strip ──────────────────────────────────
            RailHeader(title = "Today's reps", trailing = "See all →", onTrailingClick = onSeeAllReps)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
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

            state.mainTarget?.let { target ->
                TargetPill(
                    target = target,
                    onToggle = { viewModel.toggleMainTarget(target) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
        }

        ConfettiOverlay(trigger = celebration?.seq?.plus(1) ?: 0L)
        RealityGlitchOverlay(momentumScore = state.momentumScore)
        LaunchedEffect(celebration) { if (celebration != null) viewModel.consumeCelebration() }
    }
}

/** 8,243 → "8.2k" so step counts fit a tile. */
private fun compact(n: Long): String = when {
    n >= 10_000 -> "${n / 1000}k"
    n >= 1_000 -> "%.1fk".format(n / 1000f)
    else -> "$n"
}

@Composable
private fun AuroraTopBar(dateLabel: String, onLockIn: () -> Unit, onAddRep: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "VibeOS",
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

/** The hero tile: a momentum ring with the live score, level progress and a debt warning. */
@Composable
private fun MomentumRingTile(
    score: Int,
    label: String,
    levelProgress: Float,
    level: Int,
    debt: Int,
    modifier: Modifier = Modifier,
) {
    val animatedScore by animateIntAsState(targetValue = score, animationSpec = tween(700), label = "score")
    val animatedSweep by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(900),
        label = "sweep",
    )
    val ringColor = when {
        score >= 90 -> ToxicGreen
        score >= 70 -> AuroraCyan
        else -> AuroraViolet
    }
    Column(
        modifier = modifier
            .frostedGlass(cornerRadius = 22.dp)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "MOMENTUM", style = AthleticLabelStyle, color = AuroraMuted)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
            Canvas(modifier = Modifier.size(116.dp)) {
                val stroke = 11.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(
                    color = AuroraMist,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = ringColor,
                    startAngle = 135f,
                    sweepAngle = 270f * animatedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$animatedScore",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp),
                    fontWeight = FontWeight.Black,
                    color = ringColor,
                )
                Text(text = "/100", style = MaterialTheme.typography.labelSmall, color = AuroraMuted)
            }
        }
        Text(
            text = if (debt > 0) "⚠ debt $debt · $label" else label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (debt > 0) AuroraAmber else AuroraMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LinearProgressIndicator(
            progress = { levelProgress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = AuroraViolet,
            trackColor = AuroraMist,
        )
        Text(
            text = "LV $level",
            style = MaterialTheme.typography.labelSmall,
            color = AuroraMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Flame tile — triple-tap it for VibeShift (the cosmos remembers). */
@Composable
private fun FlameTile(flameLevel: Int, streak: Int, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val tapTimes = remember { longArrayOf(0L, 0L) }
    Row(
        modifier = modifier
            .frostedGlass(cornerRadius = 22.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LifeFlame(
            level = flameLevel,
            size = 58.dp,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = {
                    val now = System.currentTimeMillis()
                    if (now - tapTimes[0] < 700) {
                        tapTimes[0] = 0L; tapTimes[1] = 0L
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        VibeShiftState.toggle()
                    } else {
                        tapTimes[0] = tapTimes[1]; tapTimes[1] = now
                    }
                })
            },
        )
        Column {
            Text(
                text = FlameModel.label(flameLevel).uppercase(),
                style = AthleticLabelStyle,
                color = AuroraInk,
            )
            Text(
                text = "🔥 $streak",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = AuroraAmber,
            )
        }
    }
}

@Composable
private fun TierTile(tier: String, level: Int, momentum: Long, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .frostedGlass(cornerRadius = 22.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "${tier.uppercase()} · LV.$level", style = AthleticLabelStyle, color = AuroraInk)
        OdometerText(
            value = momentum.toInt(),
            suffix = " ⚡",
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = AuroraViolet,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(text = "lifetime momentum", style = MaterialTheme.typography.labelSmall, color = AuroraMuted)
    }
}

/** Compact bento stat tile: label, big value, optional caption / slot, tap-through. */
@Composable
private fun StatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
    extra: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .frostedGlass(cornerRadius = 20.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(12.dp),
    ) {
        Text(text = label, style = AthleticLabelStyle, color = AuroraMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        caption?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = AuroraMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        extra()
    }
}

@Composable
private fun RailHeader(title: String, trailing: String, onTrailingClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AuroraInk)
        if (trailing.isNotEmpty()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (onTrailingClick != null) FontWeight.Bold else null,
                color = if (onTrailingClick != null) AuroraViolet else AuroraMuted,
                modifier = if (onTrailingClick != null) Modifier.clickable(onClick = onTrailingClick) else Modifier,
            )
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
            .background(AuroraMist.copy(alpha = 0.5f))
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
private fun TargetPill(target: MainTargetUi, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .frostedGlass(cornerRadius = 22.dp, accent = if (target.done) AuroraViolet else null)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = AuroraViolet, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "MAIN TARGET", style = AthleticLabelStyle, color = AuroraMuted)
            Text(
                text = target.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AuroraInk,
                textDecoration = if (target.done) TextDecoration.LineThrough else null,
                maxLines = 1,
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
