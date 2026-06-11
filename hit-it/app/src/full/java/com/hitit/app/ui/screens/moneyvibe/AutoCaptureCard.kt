package com.hitit.app.ui.screens.moneyvibe

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hitit.app.ui.components.frostedGlass
import com.hitit.app.ui.theme.AuroraInk
import com.hitit.app.ui.theme.AuroraMuted
import com.hitit.app.ui.theme.ToxicGreen

/**
 * Auto-integration card (FULL flavor): Zomato/Swiggy/Uber/GPay… notifications become categorized
 * spends via [com.hitit.app.money.SpendCaptureService]. The OS gates the listener behind explicit
 * Notification Access, so this walks the user there and reflects the live enabled state (re-checked
 * on resume). The listener — and thus this card — lives only in the full build, because a
 * NotificationListenerService trips Play Protect on sideloaded APKs.
 */
@Composable
fun AutoCaptureCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun enabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    var captureOn by remember { mutableStateOf(enabled()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) captureOn = enabled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .frostedGlass(cornerRadius = 22.dp, accent = if (captureOn) ToxicGreen else null)
            .clickable {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = if (captureOn) "🤖" else "🔌", style = MaterialTheme.typography.headlineSmall)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (captureOn) "Auto-capture is ON" else "Auto-capture spends",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = if (captureOn) ToxicGreen else AuroraInk,
            )
            Text(
                text = if (captureOn) {
                    "Watching Zomato, Swiggy, Uber, Ola, Rapido, Blinkit, Zepto, GPay, PhonePe, Paytm… " +
                        "orders log themselves. Parsed on-device; nothing leaves the phone."
                } else {
                    "Tap to grant Notification Access — orders and UPI payments from Zomato, Swiggy, " +
                        "Uber, GPay & friends will log themselves. On-device only."
                },
                style = MaterialTheme.typography.labelSmall,
                color = AuroraMuted,
            )
        }
    }
}
