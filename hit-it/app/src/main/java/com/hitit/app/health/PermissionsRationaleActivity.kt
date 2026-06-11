package com.hitit.app.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hitit.app.ui.theme.HitItTheme

/**
 * Health Connect's mandatory rationale screen — shown when the user taps "why does this app want
 * health data?" in the system permission UI (Android 13- via the rationale action, 14+ via the
 * VIEW_PERMISSION_USAGE alias). It states the whole privacy story in one breath.
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HitItTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = "🫀", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Why VibeOS reads health data",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "BodyFlow shows your steps, last night's sleep and latest heart rate " +
                            "as part of your Body Pulse.\n\n" +
                            "• Read-only: VibeOS never writes or edits health records.\n" +
                            "• On-device only: the app has NO internet permission — nothing can leave your phone.\n" +
                            "• Nothing is stored: signals are read live each time you open BodyFlow.\n\n" +
                            "Revoke access anytime in Health Connect settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { finish() }) { Text("Got it") }
                }
            }
        }
    }
}
