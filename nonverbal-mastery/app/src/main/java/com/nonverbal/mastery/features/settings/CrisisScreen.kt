package com.nonverbal.mastery.features.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nonverbal.mastery.data.models.CrisisResource
import com.nonverbal.mastery.data.models.SafetyGate
import com.nonverbal.mastery.design.components.ButtonKind
import com.nonverbal.mastery.design.components.Hairline
import com.nonverbal.mastery.design.components.NvButton
import com.nonverbal.mastery.design.components.NvIcons
import com.nonverbal.mastery.design.components.Prose
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.components.TopBar
import com.nonverbal.mastery.design.components.readingColumn
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Shapes
import com.nonverbal.mastery.design.tokens.Space

/**
 * The crisis resource gate. Presented before any chapter carrying a safety gate (2.13), and
 * reachable from the home screen and Settings at all times. It never blocks the chapter:
 * refusing to teach the subject harms people, and so does teaching it without this door.
 */
@Composable
fun CrisisScreen(
    gate: SafetyGate?,
    continueLabel: String?,
    onContinue: (() -> Unit)?,
    onHome: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = Nv.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(colors.surface)) {
        TopBar(onBack = onBack, background = colors.surface)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.readingColumn(560.dp).padding(top = Space.x4, bottom = Space.x16).windowInsetsPadding(WindowInsets.navigationBars)) {
                Icon(NvIcons.Heart, null, tint = colors.brass, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(Space.x4))
                Text(gate?.resourceTitle ?: "If you're struggling right now", style = Nv.type.display, color = colors.ink, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Space.x4))
                Prose(gate?.resourceBody ?: "Please reach out to someone now. If you are in immediate danger, call your local emergency number.")
                Spacer(Modifier.height(Space.x6))
                Text(
                    "If you are in immediate danger, call your local emergency number: 911 in the US and Canada, 999 in the UK, 112 across the EU and India, 000 in Australia, 111 in New Zealand.",
                    style = Nv.type.bodySmall,
                    color = colors.ink,
                    modifier = Modifier.fillMaxWidth().clip(Shapes.card).background(colors.dontRed.copy(alpha = 0.08f)).padding(Space.x4),
                )
                Spacer(Modifier.height(Space.x8))
                SectionLabel("Free, confidential, any hour", color = colors.inkTertiary)
                Spacer(Modifier.height(Space.x2))
                gate?.regions.orEmpty().forEach { r ->
                    ResourceRow(r) { dial(context, r.contact) }
                    Hairline()
                }
                Spacer(Modifier.height(Space.x10))
                if (onContinue != null) {
                    Text(
                        "When you're ready, the chapter is here. It teaches what the body can show and what to do next: ask directly, stay, and help the person reach care. It never diagnoses, and neither should you.",
                        style = Nv.type.bodySmall, color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(Space.x4))
                    NvButton(continueLabel ?: "Continue to the chapter", onContinue)
                    Spacer(Modifier.height(Space.x2))
                }
                NvButton("Return home", onHome, kind = if (onContinue != null) ButtonKind.Quiet else ButtonKind.Secondary)
            }
        }
    }
}

@Composable
private fun ResourceRow(r: CrisisResource, onCall: () -> Unit) {
    val colors = Nv.colors
    val dialable = r.contact.any { it.isDigit() }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (dialable) Modifier.clickable(role = Role.Button, onClick = onCall) else Modifier)
            .heightIn(min = 64.dp)
            .padding(vertical = Space.x3)
            .semantics(mergeDescendants = true) { contentDescription = "${r.region}. ${r.org}. ${r.contact}" + if (dialable) ". Double tap to dial." else "" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.region.uppercase(), style = Nv.type.label, color = colors.inkTertiary)
            Spacer(Modifier.height(2.dp))
            Text(r.org, style = Nv.type.titleSmall, color = colors.ink)
            Text(r.contact, style = Nv.type.numeral.copy(fontSize = Nv.type.ui.fontSize), color = colors.accent)
        }
        if (dialable) {
            Spacer(Modifier.width(Space.x2))
            Icon(NvIcons.Phone, null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
    }
}

/** Opens the dialler with the first number in the contact string. Needs no permission. */
private fun dial(context: Context, contact: String) {
    val number = Regex("[0-9][0-9 ]{1,16}[0-9]").find(contact)?.value?.replace(" ", "") ?: return
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // A device without a dialler (some tablets): the number is on screen to copy.
    }
}
