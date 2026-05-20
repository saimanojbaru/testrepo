package com.moldable.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moldable.app.llm.ClaudeClient
import com.moldable.app.js.JsRunner
import com.moldable.app.render.ScreenView
import com.moldable.app.render.parseColor
import com.moldable.app.secure.SecureStore
import com.moldable.app.spec.Action
import com.moldable.app.spec.AppSpec
import com.moldable.app.spec.SpecStore
import com.moldable.app.state.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MoldableRoot() }
    }
}

class MoldableViewModel : ViewModel() {
    private val _spec = MutableStateFlow(AppSpec())
    val spec = _spec.asStateFlow()

    private val _currentScreenId = MutableStateFlow("home")
    val currentScreenId = _currentScreenId.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    var appState: AppState = AppState()
        private set

    fun load(ctx: android.content.Context) {
        val loaded = SpecStore.load(ctx)
        _spec.value = loaded
        appState = AppState(loaded.state)
        _currentScreenId.value = loaded.initialScreen.takeIf { id ->
            loaded.screens.any { it.id == id }
        } ?: loaded.screens.firstOrNull()?.id ?: "home"
    }

    fun navigate(target: String) {
        if (_spec.value.screens.any { it.id == target }) {
            _currentScreenId.value = target
        } else {
            _toast.value = "No such screen: $target"
        }
    }

    fun showToast(msg: String) { _toast.value = msg }
    fun clearToast() { _toast.value = null }

    fun applyAction(ctx: android.content.Context, action: Action) {
        when (action.kind.lowercase()) {
            "navigate" -> action.target?.let { navigate(it) }
            "setstate" -> action.key?.let { k -> appState.set(k, action.value ?: JsonNull) }
            "appendlist" -> action.key?.let { k ->
                val cur = appState.get(k) as? JsonArray
                val next = buildJsonArray {
                    cur?.forEach { add(it) }
                    add(action.value ?: JsonNull)
                }
                appState.set(k, next)
            }
            "removeat" -> action.key?.let { k ->
                val cur = appState.get(k) as? JsonArray ?: return
                val idx = (action.value as? JsonPrimitive)?.content?.toIntOrNull() ?: return
                val next = buildJsonArray {
                    cur.forEachIndexed { i, v -> if (i != idx) add(v) }
                }
                appState.set(k, next)
            }
            "toggle" -> action.key?.let { k ->
                val cur = (appState.get(k) as? JsonPrimitive)?.booleanOrNull ?: false
                appState.set(k, JsonPrimitive(!cur))
            }
            "toast" -> _toast.value = action.message
            "runscript" -> action.script?.let { s ->
                JsRunner.run(s, appState,
                    onToast = { _toast.value = it },
                    onNavigate = { navigate(it) }
                )
            }
        }
    }

    suspend fun mold(ctx: android.content.Context, apiKey: String, userPrompt: String) {
        _busy.value = true
        try {
            val client = ClaudeClient(apiKey)
            val newJson = client.moldSpec(SpecStore.toJson(_spec.value), userPrompt)
            val newSpec = SpecStore.fromJson(newJson)
            SpecStore.save(ctx, newSpec)
            _spec.value = newSpec
            appState = AppState(newSpec.state)
            _currentScreenId.value = newSpec.initialScreen.takeIf { id ->
                newSpec.screens.any { it.id == id }
            } ?: newSpec.screens.firstOrNull()?.id ?: "home"
            _toast.value = "Molded."
        } catch (t: Throwable) {
            _toast.value = "Mold failed: ${t.message?.take(120)}"
        } finally {
            _busy.value = false
        }
    }

    fun revert(ctx: android.content.Context) {
        val prev = SpecStore.revert(ctx)
        if (prev != null) {
            _spec.value = prev
            appState = AppState(prev.state)
            _currentScreenId.value = prev.initialScreen.takeIf { id ->
                prev.screens.any { it.id == id }
            } ?: prev.screens.firstOrNull()?.id ?: "home"
            _toast.value = "Reverted."
        } else {
            _toast.value = "Nothing to revert."
        }
    }

    fun reset(ctx: android.content.Context) {
        val def = SpecStore.reset(ctx)
        _spec.value = def
        appState = AppState(def.state)
        _currentScreenId.value = def.initialScreen
        _toast.value = "Reset to defaults."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoldableRoot(vm: MoldableViewModel = viewModel()) {
    val ctx = LocalContext.current
    val spec by vm.spec.collectAsState()
    val currentId by vm.currentScreenId.collectAsState()
    val busy by vm.busy.collectAsState()
    val toast by vm.toast.collectAsState()

    LaunchedEffect(Unit) { vm.load(ctx) }

    val isDark = when (spec.theme.mode.lowercase()) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val primary = parseColor(spec.theme.primary, Color(0xFF5B8DEF))
    val bg = parseColor(spec.theme.background, if (isDark) Color(0xFF0D1B2A) else Color(0xFFF6F8FB))
    val onBg = parseColor(spec.theme.onBackground, if (isDark) Color.White else Color(0xFF111418))

    val scheme = if (isDark)
        darkColorScheme(primary = primary, background = bg, onBackground = onBg, surface = parseColor(spec.theme.surface, Color(0xFF152339)))
    else
        lightColorScheme(primary = primary, background = bg, onBackground = onBg, surface = parseColor(spec.theme.surface, Color(0xFFFFFFFF)))

    MaterialTheme(colorScheme = scheme) {
        var showPrompt by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }

        toast?.let {
            LaunchedEffect(it) {
                Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
                vm.clearToast()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(spec.title) },
                    actions = {
                        IconButton(onClick = { vm.revert(ctx) }) {
                            Icon(Icons.Default.Undo, contentDescription = "Revert")
                        }
                        IconButton(onClick = { vm.reset(ctx) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = parseColor(spec.theme.surface, if (isDark) Color(0xFF152339) else Color(0xFFFFFFFF)),
                        titleContentColor = onBg,
                        actionIconContentColor = onBg
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showPrompt = true },
                    containerColor = primary
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = "Mold")
                }
            },
            containerColor = bg
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                val screen = spec.screens.firstOrNull { it.id == currentId } ?: spec.screens.firstOrNull()
                if (screen != null) {
                    ScreenView(
                        screen = screen,
                        state = vm.appState,
                        theme = spec.theme,
                        onAction = { vm.applyAction(ctx, it) }
                    )
                } else {
                    Text("No screens defined.", color = onBg, modifier = Modifier.padding(16.dp))
                }
                if (spec.screens.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        spec.screens.forEach { s ->
                            TextButton(onClick = { vm.navigate(s.id) }) {
                                Text(if (s.id == currentId) "[${s.title.ifBlank { s.id }}]" else s.title.ifBlank { s.id })
                            }
                        }
                    }
                }
            }

            if (busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(color = Color(0x99000000)) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            Text("Molding…", color = Color.White)
                        }
                    }
                }
            }
        }

        if (showPrompt) {
            PromptDialog(
                onDismiss = { showPrompt = false },
                vm = vm
            )
        }

        if (showSettings) {
            SettingsDialog(onDismiss = { showSettings = false })
        }
    }
}

@Composable
fun PromptDialog(
    onDismiss: () -> Unit,
    vm: MoldableViewModel
) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mold the app") },
        text = {
            Column {
                Text("Describe how the app should change.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("e.g. turn this into a todo list with a dark theme") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val key = SecureStore.getApiKey(ctx)
                if (key.isNullOrBlank()) {
                    vm.showToast("Set your Claude API key in Settings first.")
                    onDismiss()
                } else if (text.isBlank()) {
                    vm.showToast("Type a prompt first.")
                } else {
                    val prompt = text
                    onDismiss()
                    scope.launch { vm.mold(ctx, key, prompt) }
                }
            }) { Text("Mold") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(SecureStore.getApiKey(ctx).orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Text("Anthropic API key (stored encrypted on-device).")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    placeholder = { Text("sk-ant-...") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Get a key at console.anthropic.com. It never leaves your device except to call api.anthropic.com.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (key.isBlank()) SecureStore.clearApiKey(ctx)
                else SecureStore.saveApiKey(ctx, key.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
