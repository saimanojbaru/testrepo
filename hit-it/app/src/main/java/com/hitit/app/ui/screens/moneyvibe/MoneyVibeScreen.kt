package com.hitit.app.ui.screens.moneyvibe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.hitit.domain.money.ExpenseCategorizer

/**
 * MoneyVibe, amplified — the wealth psyche companion: hero pulse with week trend (and a hidden
 * Money Glitch on triple-tap), one-line spend capture, burner budget, the Wealth Analyzer brain,
 * Impulse Demons with roasts, the Vibe Tax jar, the Future Self mirror, and the weekly Burn Ritual
 * door into Shadow Self. All rule-based + on-device; notification auto-capture stays a later
 * opt-in drop.
 */
@Composable
fun MoneyVibeScreen(
    onOpenShadow: () -> Unit = {},
    viewModel: MoneyVibeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "MoneyVibe",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = AuroraInk,
            modifier = Modifier.padding(start = 22.dp, top = 14.dp),
        )
        Text(
            text = "your wealth psyche, on-device",
            style = MaterialTheme.typography.bodySmall,
            color = AuroraMuted,
            modifier = Modifier.padding(start = 22.dp, bottom = 4.dp),
        )

        MoneyHeroPulse(
            pulse = state.moneyPulse,
            pulseLabel = state.pulseLabel,
            weekSpendRupees = state.weekSpendRupees,
            trendLine = state.trendLine,
            trendIsGood = state.trendIsGood,
        )

        SpendInput(onLog = { amount, desc -> viewModel.logExpense(amount, desc) })

        BurnerBudgetCard(
            budgetRupees = state.burnerBudgetRupees,
            spentRupees = state.burnerSpendRupees,
            utilization = state.burnerUtilization,
            onSetBudget = { viewModel.setBurnerBudget(it) },
        )

        if (state.started) {
            WealthAnalyzerCard(insights = state.insights)
            if (state.vibeJarRupees > 0) VibeTaxJar(rupees = state.vibeJarRupees)
            if (state.demons.isNotEmpty()) ImpulseDemonsSection(demons = state.demons)
            FutureSelfMirror(brokeLine = state.futureBrokeLine, glowLine = state.futureGlowLine)
            BurnRitualCard(onClick = onOpenShadow)
        }

        if (state.expenses.isNotEmpty()) {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = AuroraInk,
                modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 4.dp),
            )
            state.expenses.forEach { expense ->
                ExpenseRow(expense = expense, onDelete = { viewModel.deleteExpense(expense.id) })
            }
        } else {
            Text(
                text = "No spends logged yet — try \"450 Starbucks\". We'll guess the category. ☕",
                style = MaterialTheme.typography.bodySmall,
                color = AuroraMuted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

/** Hero: huge pulse + week trend. Triple-tap the number for the hidden Money Glitch. */
@Composable
private fun MoneyHeroPulse(
    pulse: Int,
    pulseLabel: String,
    weekSpendRupees: Long,
    trendLine: String,
    trendIsGood: Boolean,
) {
    val haptics = LocalHapticFeedback.current
    val tapTimes = remember { longArrayOf(0L, 0L) }
    val pulseColor = when {
        pulse >= 70 -> ToxicGreen
        pulse >= 45 -> AuroraAmber
        else -> AuroraPink
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .frostedGlass(cornerRadius = 26.dp)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "MONEY PULSE", style = AthleticLabelStyle, color = AuroraMuted)
        Text(
            text = "$pulse",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, fontWeight = FontWeight.Black),
            color = pulseColor,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = {
                    val now = System.currentTimeMillis()
                    if (now - tapTimes[0] < 700) {
                        tapTimes[0] = 0L; tapTimes[1] = 0L
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        VibeShiftState.toggle() // Money Glitch — same cosmos, different dimension
                    } else {
                        tapTimes[0] = tapTimes[1]; tapTimes[1] = now
                    }
                })
            },
        )
        Text(
            text = pulseLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = pulseColor,
        )
        if (trendLine.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "₹$weekSpendRupees this week · $trendLine",
                style = MaterialTheme.typography.labelMedium,
                color = if (trendIsGood) AuroraCyan else AuroraPink,
            )
        }
    }
}

@Composable
private fun WealthAnalyzerCard(insights: List<InsightUi>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .frostedGlass(cornerRadius = 24.dp, accent = if (insights.any { it.severity >= 5 }) AuroraPink else null)
            .padding(16.dp),
    ) {
        Text(
            text = "🧠 WEALTH ANALYZER",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = AuroraCyan,
        )
        Spacer(Modifier.height(8.dp))
        insights.forEach { insight ->
            val accent = when {
                insight.severity >= 5 -> AuroraPink
                insight.severity >= 3 -> AuroraAmber
                insight.severity >= 1 -> AuroraViolet
                else -> AuroraCyan
            }
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = insight.emoji, style = MaterialTheme.typography.titleMedium)
                Column {
                    Text(
                        text = insight.headline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = accent,
                    )
                    Text(
                        text = insight.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = AuroraInk,
                    )
                }
            }
        }
        Text(
            text = "Rule-based, fully on-device. The full build's LLM can rephrase these — the analysis never needs it.",
            style = MaterialTheme.typography.labelSmall,
            color = AuroraMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun VibeTaxJar(rupees: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .frostedGlass(cornerRadius = 22.dp, accent = AuroraCyan)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "🫙", style = MaterialTheme.typography.headlineMedium)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Future Self Jar",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = AuroraInk,
            )
            Text(
                text = "Every impulse buy auto-rounds up to the next ₹10 — the skim lands here.",
                style = MaterialTheme.typography.labelSmall,
                color = AuroraMuted,
            )
        }
        Text(
            text = "₹$rupees",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = AuroraCyan,
        )
    }
}

@Composable
private fun ImpulseDemonsSection(demons: List<DemonUi>) {
    Text(
        text = "👹 IMPULSE DEMONS THIS WEEK",
        style = AthleticLabelStyle,
        color = AuroraPink,
        modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 4.dp),
    )
    demons.forEach { demon ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .frostedGlass(cornerRadius = 18.dp, accent = AuroraPink)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${demon.description} · ₹${demon.amountRupees}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = AuroraInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "“${demon.roast}”",
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroraPink,
                )
            }
        }
    }
}

/** Two timelines, two avatars: broke-you vs glowing-you, from the same week of fun money. */
@Composable
private fun FutureSelfMirror(brokeLine: String, glowLine: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .frostedGlass(cornerRadius = 24.dp)
            .padding(16.dp),
    ) {
        Text(text = "🔮 FUTURE SELF MIRROR", style = AthleticLabelStyle, color = AuroraMuted)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "😵", style = MaterialTheme.typography.displaySmall)
                Text(
                    text = brokeLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroraPink,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "🤑", style = MaterialTheme.typography.displaySmall)
                Text(
                    text = glowLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = ToxicGreen,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Same money, two timelines. Projection, not financial advice — but the math is the math.",
            style = MaterialTheme.typography.labelSmall,
            color = AuroraMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BurnRitualCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .frostedGlass(cornerRadius = 22.dp, accent = AuroraViolet)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "🔥", style = MaterialTheme.typography.headlineSmall)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Weekly Burn Ritual",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = AuroraInk,
            )
            Text(
                text = "Face Shadow Self with this week's spending. It already knows about the demons.",
                style = MaterialTheme.typography.labelSmall,
                color = AuroraMuted,
            )
        }
    }
}

@Composable
private fun SpendInput(onLog: (Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val previewCategory = remember(desc) {
        if (desc.isBlank()) null else ExpenseCategorizer.categorize(desc)
    }
    val valid = (amount.toLongOrNull() ?: 0L) > 0 && desc.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .frostedGlass(cornerRadius = 24.dp)
            .padding(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() }.take(7) },
                modifier = Modifier.weight(0.38f),
                placeholder = { Text("₹ 450") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuroraAmber,
                    unfocusedBorderColor = AuroraMuted.copy(alpha = 0.3f),
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = desc,
                onValueChange = { desc = it },
                modifier = Modifier.weight(0.62f),
                placeholder = { Text("Starbucks / Swiggy / Uber…") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AuroraAmber,
                    unfocusedBorderColor = AuroraMuted.copy(alpha = 0.3f),
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (previewCategory != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AuroraAmber.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "${previewCategory.emoji} ${previewCategory.label}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = AuroraInk,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    onLog(amount.toLongOrNull() ?: 0L, desc)
                    amount = ""
                    desc = ""
                },
                enabled = valid,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (valid) AuroraViolet else AuroraMuted.copy(alpha = 0.2f)),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Log spend", tint = Color.White)
            }
        }
    }
}

@Composable
private fun BurnerBudgetCard(
    budgetRupees: Long,
    spentRupees: Long,
    utilization: Float?,
    onSetBudget: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .frostedGlass(cornerRadius = 24.dp, accent = if (utilization != null && utilization > 1f) AuroraPink else null)
            .padding(16.dp),
    ) {
        Text(text = "🔥 BURNER BUDGET", style = AthleticLabelStyle, color = AuroraMuted)
        Text(
            text = if (budgetRupees > 0) "₹$spentRupees of ₹$budgetRupees fun money this week"
            else "Cap your weekly fun money",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = AuroraInk,
        )
        if (budgetRupees > 0 && utilization != null) {
            LinearProgressIndicator(
                progress = { utilization.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = if (utilization > 1f) AuroraPink else AuroraAmber,
                trackColor = AuroraMist,
            )
            Text(
                text = when {
                    utilization > 1f -> "Budget torched — the Coach will hear about this. 🥲"
                    utilization > 0.8f -> "Running hot. Maybe skip the next impulse?"
                    else -> "Comfortably in the green. Vibes sustainable."
                },
                style = MaterialTheme.typography.labelSmall,
                color = AuroraMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(0L to "Off", 500L to "₹500", 1000L to "₹1k", 2000L to "₹2k").forEach { (value, label) ->
                val selected = budgetRupees == value
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) AuroraViolet else AuroraMist)
                        .clickable { onSetBudget(value) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else AuroraMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseUi, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .frostedGlass(cornerRadius = 18.dp, accent = if (expense.impulse) AuroraPink else null)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = expense.categoryEmoji, style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = expense.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = AuroraInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(expense.categoryLabel)
                    append(" · ")
                    append(expense.dateLabel)
                    if (expense.impulse) append("  ·  ⚡ impulse")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (expense.impulse) AuroraPink else AuroraMuted,
            )
        }
        Text(
            text = "₹${expense.amountRupees}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = AuroraInk,
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp).padding(start = 4.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Delete", tint = AuroraMuted, modifier = Modifier.size(16.dp))
        }
    }
}
