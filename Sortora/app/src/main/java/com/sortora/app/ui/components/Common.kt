package com.sortora.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sortora.app.R
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.ui.design.Art
import com.sortora.app.ui.design.Illustration
import com.sortora.app.ui.design.Radius
import com.sortora.app.ui.design.Reveal
import com.sortora.app.ui.design.SoftCard
import com.sortora.app.ui.design.Sortora
import com.sortora.app.ui.design.Space
import com.sortora.app.ui.design.hairline
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** The "nothing leaves your phone" card on Home and in Settings. */
@Composable
fun PrivacyBanner(modifier: Modifier = Modifier) {
    val c = Sortora.colors
    SoftCard(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Space.md))
            Column {
                Text(stringResource(R.string.privacy_banner_title), style = MaterialTheme.typography.titleSmall, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.privacy_banner_body), style = MaterialTheme.typography.bodySmall, color = c.inkSecondary)
            }
        }
    }
}

/** Empty or finished states: a slow-moving illustration, a serif title and one line of help. */
@Composable
fun EmptyState(
    art: Art,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = Sortora.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xxl, vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Illustration(art, size = 152.dp)
        Spacer(Modifier.height(Space.lg))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Space.lg))
            action()
        }
    }
}

/** Centres an [EmptyState] in the available space and fades it in. */
@Composable
fun CenteredEmptyState(art: Art, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Reveal(visible = true) { EmptyState(art, title, message, action = action) }
    }
}

/** Thumbnail for a local content:// URI (images directly, videos via a decoded frame). */
@Composable
fun MediaThumbnail(uri: String, kind: MediaKind, modifier: Modifier = Modifier, size: Dp = 56.dp, radius: Dp = 12.dp) {
    val c = Sortora.colors
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(c.thumbnail)
            .hairline(shape, c.hairline),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size),
        )
        if (kind == MediaKind.VIDEO) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(size / 14)
                    .size((size / 3.2f).coerceAtLeast(16.dp))
                    .clip(Radius.Pill)
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_video),
                    tint = Color.White,
                    modifier = Modifier.size((size / 4.4f).coerceAtLeast(12.dp)),
                )
            }
        }
    }
}

object Formatters {
    fun bytes(bytes: Long): String {
        if (bytes < 0) return "—"
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "$bytes B" else String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
    }

    fun dateTime(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    fun date(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

    fun percent(value: Float): String = "${(value * 100).toInt()}%"
}
