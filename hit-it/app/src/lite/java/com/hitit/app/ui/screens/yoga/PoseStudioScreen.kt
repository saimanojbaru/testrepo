package com.hitit.app.ui.screens.yoga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hitit.app.ui.components.frostedGlass
import com.hitit.app.ui.theme.AuroraInk
import com.hitit.app.ui.theme.AuroraMuted

/**
 * Lite-flavor stand-in for the Pose Studio. The live camera judge needs the on-device vision model
 * (MediaPipe), which the lite build deliberately omits to stay small and universal — same deal as
 * the LLM coach. Same FQN/signature as the full implementation so shared navigation compiles.
 */
@Composable
fun PoseStudioScreen(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .frostedGlass(cornerRadius = 26.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "🧘‍♀️📷", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "The AI judge lives in the full build",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = AuroraInk,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Live pose grading runs an on-device vision model, which this lean build " +
                    "leaves out to stay tiny and install anywhere. Grab the FULL APK from the " +
                    "repo's dist folder — your pose library here carries over.",
                style = MaterialTheme.typography.bodySmall,
                color = AuroraMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDone) { Text("Back") }
        }
    }
}
