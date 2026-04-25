package com.spotifymashup.generator.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotifymashup.generator.data.HookDto
import com.spotifymashup.generator.ui.components.HookCard
import com.spotifymashup.generator.ui.theme.BrandPrimary
import com.spotifymashup.generator.ui.theme.BrandSecondary
import com.spotifymashup.generator.viewmodel.MashupViewModel

@Composable
fun HomeScreen(vm: MashupViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val systemBars = WindowInsets.systemBars.asPaddingValues()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scroll)
            .padding(top = systemBars.calculateTopPadding() + 24.dp,
                     bottom = systemBars.calculateBottomPadding() + 32.dp,
                     start = 20.dp, end = 20.dp),
    ) {
        Text(
            "Mashup Studio",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "AI-picked viral hooks · auto BPM & key match",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        // Backend URL
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("BACKEND SERVER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = vm::setBaseUrl,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }

        TrackInputCard(
            title = "TRACK A · VOCALS",
            accent = BrandPrimary,
            query = state.trackA.query,
            hooks = state.trackA.hooks,
            selectedHook = state.trackA.selectedHook,
            otherSelected = state.trackB.selectedHook != null,
            analyzing = state.trackA.analyzing,
            trackName = state.trackA.trackName,
            artistName = state.trackA.artistName,
            onQueryChange = { vm.setQuery(forA = true, q = it) },
            onFindHooks = { vm.findHooks(forA = true) },
            onSelect = { vm.selectHook(forA = true, h = it) },
        )

        Spacer(Modifier.height(14.dp))

        TrackInputCard(
            title = "TRACK B · INSTRUMENTAL",
            accent = BrandSecondary,
            query = state.trackB.query,
            hooks = state.trackB.hooks,
            selectedHook = state.trackB.selectedHook,
            otherSelected = state.trackA.selectedHook != null,
            analyzing = state.trackB.analyzing,
            trackName = state.trackB.trackName,
            artistName = state.trackB.artistName,
            onQueryChange = { vm.setQuery(forA = false, q = it) },
            onFindHooks = { vm.findHooks(forA = false) },
            onSelect = { vm.selectHook(forA = false, h = it) },
        )

        Spacer(Modifier.height(14.dp))

        AnimatedVisibility(
            visible = state.compatibility != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            CompatibilityCard(state.compatibility)
        }

        AdvancedCard(
            open = state.advancedOpen,
            youtubeOnly = state.youtubeOnly,
            applyPitchShift = state.applyPitchShift,
            bpmA = state.bpmOverrideA,
            bpmB = state.bpmOverrideB,
            onToggle = vm::toggleAdvanced,
            onYoutube = vm::setYoutubeOnly,
            onPitch = vm::setPitchShift,
            onBpmA = vm::setBpmOverrideA,
            onBpmB = vm::setBpmOverrideB,
        )

        Spacer(Modifier.height(18.dp))

        Button(
            modifier = Modifier.fillMaxWidth().height(58.dp),
            onClick = vm::generate,
            shape = RoundedCornerShape(28.dp),
        ) {
            Text("Generate mashup", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TrackInputCard(
    title: String,
    accent: Color,
    query: String,
    hooks: List<HookDto>,
    selectedHook: HookDto?,
    otherSelected: Boolean,
    analyzing: Boolean,
    trackName: String?,
    artistName: String?,
    onQueryChange: (String) -> Unit,
    onFindHooks: () -> Unit,
    onSelect: (HookDto) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(6.dp).height(22.dp).background(accent))
                Spacer(Modifier.width(10.dp))
                Text(
                    title, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (selectedHook != null) {
                    Text(
                        "✓ ${selectedHook.label}",
                        color = accent,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste a Spotify URL or describe a song") },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(50.dp),
                onClick = onFindHooks,
                enabled = !analyzing && query.isNotBlank(),
                shape = RoundedCornerShape(24.dp),
            ) {
                if (analyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Analysing…")
                } else {
                    Text("🔥  Find viral hooks")
                }
            }
            if (hooks.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                if (trackName != null && artistName != null) {
                    Text(
                        "“$trackName” · $artistName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hooks.forEach { h ->
                        HookCard(
                            hook = h,
                            selected = selectedHook?.startMs == h.startMs,
                            isAnotherSelected = selectedHook != null,
                            onSelect = { onSelect(h) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompatibilityCard(c: com.spotifymashup.generator.data.CompatibilityResponse?) {
    if (c == null) return
    val pct = (c.overallScore * 100).toInt()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Compatibility",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(BrandPrimary, BrandSecondary),
                            ),
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("$pct%", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row {
                StatBlock("Tempo",
                    if (c.bpmA != null && c.bpmB != null) "${c.bpmA.toInt()} ↔ ${c.bpmB.toInt()}" else "—",
                    Modifier.weight(1f))
                StatBlock("Key",
                    listOfNotNull(c.keyALabel, c.keyBLabel).joinToString(" ↔ ").ifEmpty { "—" },
                    Modifier.weight(1f))
                StatBlock("Energy", "${(c.energyScore * 100).toInt()}%", Modifier.weight(1f))
            }
            val advice = remember(c) { buildAdvice(c) }
            if (advice.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    advice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun buildAdvice(c: com.spotifymashup.generator.data.CompatibilityResponse): String {
    val parts = mutableListOf<String>()
    c.suggestedTempoRatio?.let {
        val pct = ((it - 1f) * 100).toInt()
        if (pct != 0) parts += "Stretch A by ${if (pct > 0) "+" else ""}$pct%"
    }
    c.suggestedPitchShift?.takeIf { it != 0 }?.let {
        parts += "Pitch-shift A by $it semitones"
    }
    if (parts.isEmpty()) {
        parts += if (c.overallScore >= 0.7f) "Strong match — minimal correction needed." else "Manual BPM/key may improve the result."
    }
    return parts.joinToString(". ")
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdvancedCard(
    open: Boolean,
    youtubeOnly: Boolean,
    applyPitchShift: Boolean,
    bpmA: String,
    bpmB: String,
    onToggle: () -> Unit,
    onYoutube: (Boolean) -> Unit,
    onPitch: (Boolean) -> Unit,
    onBpmA: (String) -> Unit,
    onBpmB: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (open) "▴  Advanced" else "▾  Advanced",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(visible = open) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = youtubeOnly, onCheckedChange = onYoutube,
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                        )
                        Text("YouTube-only mode (skip Spotify lookup)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = applyPitchShift, onCheckedChange = onPitch)
                        Text("Auto key-compatibility pitch shift")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = bpmA, onValueChange = onBpmA,
                            modifier = Modifier.weight(1f),
                            label = { Text("BPM A override") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = bpmB, onValueChange = onBpmB,
                            modifier = Modifier.weight(1f),
                            label = { Text("BPM B override") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                }
            }
        }
    }
}
