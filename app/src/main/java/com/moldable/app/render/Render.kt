package com.moldable.app.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moldable.app.spec.Action
import com.moldable.app.spec.Screen
import com.moldable.app.spec.Theme
import com.moldable.app.spec.Widget
import com.moldable.app.state.AppState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ScreenView(
    screen: Screen,
    state: AppState,
    onAction: (Action) -> Unit,
    theme: Theme
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = parseColor(theme.background, Color(0xFF0D1B2A))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (screen.title.isNotBlank()) {
                Text(
                    text = screen.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = parseColor(theme.onBackground, Color.White)
                )
                Spacer(Modifier.height(12.dp))
            }
            screen.widgets.forEach { w ->
                WidgetView(w, state, onAction, theme)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun WidgetView(
    widget: Widget,
    state: AppState,
    onAction: (Action) -> Unit,
    theme: Theme
) {
    val onBg = parseColor(theme.onBackground, Color.White)
    val primary = parseColor(theme.primary, Color(0xFF5B8DEF))
    val onPrimary = parseColor(theme.onPrimary, Color.White)
    val surface = parseColor(theme.surface, Color(0xFF152339))

    when (widget.type.lowercase()) {
        "text" -> Text(widget.text.orEmpty(), color = onBg)
        "heading" -> Text(
            widget.text.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            color = onBg
        )
        "spacer" -> Spacer(Modifier.height(16.dp))
        "input" -> {
            val key = widget.bind ?: widget.id ?: "value"
            OutlinedTextField(
                value = state.getString(key),
                onValueChange = { state.setString(key, it) },
                placeholder = { Text(widget.placeholder.orEmpty()) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        "checkbox" -> {
            val key = widget.bind ?: widget.id ?: "flag"
            val checked = (state.get(key) as? JsonPrimitive)?.booleanOrNull ?: false
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { state.set(key, JsonPrimitive(it)) }
                )
                Spacer(Modifier.padding(start = 4.dp))
                Text(widget.text.orEmpty(), color = onBg)
            }
        }
        "button" -> {
            Button(
                onClick = { widget.onClick?.let(onAction) },
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = onPrimary)
            ) { Text(widget.text ?: "Button") }
        }
        "list" -> {
            val key = widget.itemsFrom ?: widget.id ?: "items"
            val arr = state.get(key) as? JsonArray
            val list = arr?.map { it.jsonPrimitive.content } ?: widget.items.orEmpty()
            Surface(color = surface, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (list.isEmpty()) Text("(empty)", color = onBg)
                    list.forEach { item ->
                        Text("• $item", color = onBg, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
        "row" -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                widget.children.orEmpty().forEach { c -> WidgetView(c, state, onAction, theme) }
            }
        }
        "col", "column" -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                widget.children.orEmpty().forEach { c -> WidgetView(c, state, onAction, theme) }
            }
        }
        else -> Text("(unknown widget: ${widget.type})", color = onBg)
    }
}

fun parseColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val s = hex.removePrefix("#")
        val v = s.toLong(16)
        when (s.length) {
            6 -> Color(0xFF000000 or v)
            8 -> Color(v)
            else -> fallback
        }
    } catch (_: Throwable) {
        fallback
    }
}
