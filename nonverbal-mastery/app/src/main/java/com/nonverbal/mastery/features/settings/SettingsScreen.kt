package com.nonverbal.mastery.features.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nonverbal.mastery.AppContainer
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.data.repo.DesignPrefs
import com.nonverbal.mastery.data.repo.JournalBackup
import com.nonverbal.mastery.data.repo.MotionMode
import com.nonverbal.mastery.data.repo.ProgressRepository
import com.nonverbal.mastery.data.repo.ThemeMode
import com.nonverbal.mastery.design.components.ButtonKind
import com.nonverbal.mastery.design.components.ChoiceRow
import com.nonverbal.mastery.design.components.Hairline
import com.nonverbal.mastery.design.components.NavRow
import com.nonverbal.mastery.design.components.NvButton
import com.nonverbal.mastery.design.components.NvIcons
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.components.TopBar
import com.nonverbal.mastery.design.components.readingColumn
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Newsreader
import com.nonverbal.mastery.design.tokens.Shapes
import com.nonverbal.mastery.design.tokens.Space
import com.nonverbal.mastery.features.level.levelWordCount
import com.nonverbal.mastery.features.level.pagesFor
import com.nonverbal.mastery.ui.nav.appViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val prefs: StateFlow<DesignPrefs> = c.settings.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, DesignPrefs())
    fun setTheme(m: ThemeMode) = viewModelScope.launch { c.settings.setTheme(m) }
    fun setMotion(m: MotionMode) = viewModelScope.launch { c.settings.setMotion(m) }
    fun setSize(s: Int) = viewModelScope.launch { c.settings.setReadingSize(s) }
    fun systemAnimationsOff() = c.settings.systemAnimationsOff()

    suspend fun exportJson(contentVersion: Int): String = ProgressRepository.encode(c.progress.exportJournal(contentVersion))
    suspend fun importJournal(backup: JournalBackup) = c.progress.importJournal(backup)
    fun reset() = viewModelScope.launch { c.progress.resetAll() }
}

/** Writes the journal to the cache and hands it to the share sheet. Nothing is sent by the app itself. */
object JournalExport {
    suspend fun share(context: Context, json: String) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            File(dir, "nonverbal-mastery-journal-$stamp.json").apply { writeText(json) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Nonverbal Mastery journal")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export your journal").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun read(context: Context, uri: Uri): JournalBackup = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("empty file")
        ProgressRepository.decode(text)
    }
}

@Composable
fun SettingsRouteScreen(curriculum: Curriculum, onBack: () -> Unit, onEthics: () -> Unit, onCrisis: () -> Unit, onReflections: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<JournalBackup?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            pendingImport = runCatching { JournalExport.read(context, uri) }.getOrElse {
                message = "That file isn't a Nonverbal Mastery journal, so nothing was changed."
                null
            }
        }
    }
    SettingsScreen(
        curriculum = curriculum,
        prefs = prefs,
        systemAnimationsOff = vm.systemAnimationsOff(),
        message = message,
        pendingImport = pendingImport,
        onBack = onBack,
        onTheme = { vm.setTheme(it) },
        onMotion = { vm.setMotion(it) },
        onSize = { vm.setSize(it) },
        onExport = { scope.launch { JournalExport.share(context, vm.exportJson(curriculum.pack.version)) } },
        onImport = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        onConfirmImport = {
            val backup = pendingImport ?: return@SettingsScreen
            scope.launch {
                runCatching { vm.importJournal(backup) }
                    .onSuccess { message = "Journal restored: ${backup.progress.size} chapters, ${backup.bookmarks.size} saved plates." }
                    .onFailure { message = "The import didn't finish. Your journal is unchanged." }
                pendingImport = null
            }
        },
        onCancelImport = { pendingImport = null },
        onReset = { vm.reset(); message = "Progress, reflections and saved plates have been erased." },
        onEthics = onEthics,
        onCrisis = onCrisis,
        onReflections = onReflections,
    )
}

@Composable
fun SettingsScreen(
    curriculum: Curriculum,
    prefs: DesignPrefs,
    systemAnimationsOff: Boolean,
    message: String?,
    pendingImport: JournalBackup?,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onMotion: (MotionMode) -> Unit,
    onSize: (Int) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onReset: () -> Unit,
    onEthics: () -> Unit,
    onCrisis: () -> Unit,
    onReflections: () -> Unit,
) {
    val colors = Nv.colors
    var confirmingReset by remember { mutableStateOf(false) }
    val words = remember(curriculum) { curriculum.levels.values.sumOf { levelWordCount(it) } }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        TopBar(title = "Settings", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).readingColumn(560.dp, Space.listGutter).padding(bottom = Space.x16)) {
            if (message != null) {
                Text(
                    message,
                    style = Nv.type.marginalia,
                    color = colors.ink,
                    modifier = Modifier.padding(top = Space.x4).fillMaxWidth().clip(Shapes.card).background(colors.surfaceSunken).padding(Space.x4)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Group("Appearance") {
                ChoiceRow("Match the system", prefs.theme == ThemeMode.SYSTEM, { onTheme(ThemeMode.SYSTEM) })
                ChoiceRow("Paper", prefs.theme == ThemeMode.LIGHT, { onTheme(ThemeMode.LIGHT) }, detail = "Warm bone, near-black ink")
                ChoiceRow("Ink", prefs.theme == ThemeMode.DARK, { onTheme(ThemeMode.DARK) }, detail = "Deep slate, warm light text")
            }
            Group("Reading size") {
                val name = when (prefs.readingSize) { in 15..16 -> "Small"; 17, 18 -> "Default"; in 19..20 -> "Large"; else -> "Extra large" }
                Row {
                    Text(name, style = Nv.type.ui, color = colors.ink, modifier = Modifier.weight(1f))
                    Text("${prefs.readingSize} sp", style = Nv.type.numeral, color = colors.inkTertiary)
                }
                Slider(
                    value = prefs.readingSize.toFloat(),
                    onValueChange = { onSize(Math.round(it)) },
                    valueRange = 15f..22f,
                    steps = 6,
                    modifier = Modifier.semantics { stateDescription = "$name, ${prefs.readingSize}" },
                    colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.divider,
                        activeTickColor = colors.onAccent, inactiveTickColor = colors.inkFaint),
                )
                Row {
                    listOf("Small", "Default", "Large", "Extra large").forEach {
                        Text(it, style = Nv.type.uiSmall, color = colors.inkTertiary, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(Space.x4))
                Text(
                    "The baseline is not a pose. It is the range a person moves within when nothing in particular is happening, and every reading starts from it.",
                    style = TextStyle(fontFamily = Newsreader, fontSize = prefs.readingSize.sp, lineHeight = (prefs.readingSize * 1.65f).sp),
                    color = colors.ink,
                    modifier = Modifier.fillMaxWidth().clip(Shapes.card).background(colors.surface).border(Shapes.hairline, colors.divider, Shapes.card).padding(Space.x4),
                )
            }
            Group("Motion") {
                ChoiceRow("Follow the system", prefs.motion == MotionMode.SYSTEM, { onMotion(MotionMode.SYSTEM) },
                    detail = if (systemAnimationsOff) "Your system has animations off, so motion is already reduced" else "Transitions, the mastery trace, the breathing plate")
                ChoiceRow("Reduce motion", prefs.motion == MotionMode.REDUCED, { onMotion(MotionMode.REDUCED) },
                    detail = "Every animation is replaced by its still version")
            }
            Group("Your journal") {
                Text(
                    "Reflections, field notes, quiz history, progress and saved plates stay on this phone. They are not backed up anywhere unless you export them.",
                    style = Nv.type.uiSmall, color = colors.inkSecondary,
                )
                Spacer(Modifier.height(Space.x2))
                NavRow("Read your reflections", onReflections, leading = NvIcons.Pen)
                NavRow("Export journal", onExport, detail = "A JSON file, through the share sheet", leading = NvIcons.Share)
                NavRow("Import journal", onImport, detail = "Restores from an exported file", leading = NvIcons.Import)
                if (pendingImport != null) {
                    Column(Modifier.fillMaxWidth().clip(Shapes.card).background(colors.surfaceSunken).padding(Space.x4)) {
                        Text(
                            "Replace your current journal with this file? It holds ${pendingImport.progress.size} chapters of progress, " +
                                "${pendingImport.progress.count { it.reflection.isNotBlank() }} reflections and ${pendingImport.bookmarks.size} saved plates. " +
                                "What's on this phone now will be replaced.",
                            style = Nv.type.bodySmall, color = colors.ink,
                        )
                        Spacer(Modifier.height(Space.x3))
                        Row {
                            NvButton("Replace", onConfirmImport)
                            Spacer(Modifier.width(Space.x2))
                            NvButton("Cancel", onCancelImport, kind = ButtonKind.Quiet)
                        }
                    }
                }
            }
            Group("The book") {
                Row { Text("Content edition", style = Nv.type.ui, color = colors.ink, modifier = Modifier.weight(1f)); Text("v${curriculum.pack.version}", style = Nv.type.numeral, color = colors.inkTertiary) }
                Spacer(Modifier.height(Space.x2))
                Text(
                    "${curriculum.writtenTotal} of ${curriculum.plannedTotal} chapters written  ·  ${"%,d".format(words)} words  ·  about ${pagesFor(words)} pages  ·  ${curriculum.pack.poses.size} plates  ·  compiled ${curriculum.pack.generatedAt}",
                    style = Nv.type.numeral, color = colors.inkTertiary,
                )
                Spacer(Modifier.height(Space.x2))
                NavRow("Read the ethics statement", onEthics, leading = NvIcons.Book)
                NavRow("Crisis resources", onCrisis, detail = "Phone and text lines by region", leading = NvIcons.Heart)
            }
            Group("Privacy") {
                Text(
                    "This app has no network permission. It cannot send anything anywhere. There is no analytics, no crash reporting and no tracking of any kind. It is a book.",
                    style = Nv.type.bodySmall, color = colors.inkSecondary,
                )
            }
            Group("Start again") {
                if (!confirmingReset) {
                    NvButton("Reset progress", { confirmingReset = true }, kind = ButtonKind.Secondary, tint = colors.dontText)
                } else {
                    Text(
                        "This erases every mastered chapter, quiz attempt, field note, reflection and saved plate on this phone. It cannot be undone. " +
                            "If you might want them later, export your journal first.",
                        style = Nv.type.bodySmall, color = colors.ink,
                    )
                    Spacer(Modifier.height(Space.x3))
                    Row {
                        NvButton("Erase everything", { confirmingReset = false; onReset() }, tint = colors.dontText)
                        Spacer(Modifier.width(Space.x2))
                        NvButton("Keep it", { confirmingReset = false }, kind = ButtonKind.Quiet)
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(Space.x8))
    SectionLabel(title, color = Nv.colors.inkTertiary)
    Spacer(Modifier.height(Space.x2))
    content()
    Spacer(Modifier.height(Space.x4))
    Hairline()
}
