package com.hitit.app.ui.screens.shadow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.ui.components.frostedGlass
import com.hitit.app.ui.theme.AuroraAmber
import com.hitit.app.ui.theme.AuroraInk
import com.hitit.app.ui.theme.AuroraMuted
import com.hitit.app.ui.theme.AuroraPink
import com.hitit.app.ui.theme.AuroraViolet

/**
 * Shadow Self — the unhinged dark mirror. The Coach, off the leash: roasts your worst patterns in
 * Gen-Z slang, sorted worst-first, each ending in the fix. All on-device, all private.
 */
@Composable
fun ShadowSelfScreen(
    onBack: () -> Unit,
    viewModel: ShadowSelfViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AuroraInk)
            }
            Column {
                Text(
                    text = "🕶️ Shadow Self",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = AuroraInk,
                )
                Text(
                    text = "your unhinged dark mirror · stays on this phone",
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroraMuted,
                )
            }
        }

        // The verdict — big and a little menacing.
        Text(
            text = state.verdict,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = AuroraPink,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
        )

        state.roasts.forEach { roast ->
            val accent = when {
                roast.severity >= 5 -> AuroraPink
                roast.severity >= 3 -> AuroraViolet
                roast.severity >= 1 -> AuroraAmber
                else -> AuroraViolet
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 7.dp)
                    .frostedGlass(cornerRadius = 22.dp, accent = if (roast.severity >= 3) accent else null)
                    .padding(16.dp),
            ) {
                Text(
                    text = roast.headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = accent,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = roast.line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuroraInk,
                )
            }
        }

        Text(
            text = "Shadow Self is rule-based and private. Turn on the on-device LLM (full build) to let it improvise.",
            style = MaterialTheme.typography.labelSmall,
            color = AuroraMuted,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )

        Spacer(Modifier.height(24.dp))
    }
}
