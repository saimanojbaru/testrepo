package com.hitit.app.ui.screens.yoga

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.pose.PoseAnalyzer
import com.hitit.app.ui.components.frostedGlass
import com.hitit.app.ui.theme.AuroraCyan
import com.hitit.app.ui.theme.AuroraInk
import com.hitit.app.ui.theme.AuroraMuted
import com.hitit.app.ui.theme.AuroraPink
import com.hitit.app.ui.theme.AuroraViolet
import com.hitit.app.ui.theme.ToxicGreen
import com.hitit.domain.pose.HoldTracker
import com.hitit.domain.pose.PoseLandmark
import com.hitit.domain.pose.PoseMatcher
import java.util.concurrent.Executors

/**
 * Pose Studio (full flavor): the live camera judge.
 *  - CAPTURE mode (no poseId): strike the pose, the landmarker reads your body, name + save —
 *    that frame's 33 landmarks become the reference.
 *  - PRACTICE mode: every frame is scored against the reference by the domain PoseMatcher; you get
 *    a live 0..100, the worst-joint fixes, and a clean-hold timer. Finish logs the session.
 * On-device only; frames never leave the camera pipeline.
 */
@Composable
fun PoseStudioScreen(
    onDone: () -> Unit,
    viewModel: PoseStudioViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCamera = granted }

    LaunchedEffect(Unit) {
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCamera) {
        CameraPermissionPane(
            onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onBack = onDone,
        )
        return
    }

    StudioCamera(viewModel = viewModel, onDone = onDone)
}

@Composable
private fun StudioCamera(viewModel: PoseStudioViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reference by viewModel.reference.collectAsStateWithLifecycle()
    val referencePoseName by viewModel.poseName.collectAsStateWithLifecycle()

    // Live judge state — written from the MediaPipe worker thread (snapshot state is thread-safe).
    var bodyInFrame by remember { mutableStateOf(false) }
    var liveLandmarks by remember { mutableStateOf<List<PoseLandmark>?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var bestScore by remember { mutableIntStateOf(0) }
    var hints by remember { mutableStateOf<List<String>>(emptyList()) }
    var holdMs by remember { mutableLongStateOf(0L) }
    val holdTracker = remember { HoldTracker() }

    val analyzer = remember {
        PoseAnalyzer(context) { pose ->
            liveLandmarks = pose
            bodyInFrame = pose != null
            val ref = viewModel.reference.value
            if (pose != null && ref != null) {
                val match = PoseMatcher.match(ref, pose)
                if (match != null) {
                    score = match.score
                    if (match.score > bestScore) bestScore = match.score
                    hints = match.feedback.take(2).map { it.hint }
                    holdTracker.feed(SystemClock.uptimeMillis(), match.score)
                    holdMs = holdTracker.currentHoldMs
                }
            }
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        cameraProvider = provider
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
    }
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Top bar: back + mode title.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x990C0A1F))
                .padding(top = 28.dp, bottom = 8.dp, start = 6.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = if (viewModel.isCapture) "🧘 Teach a pose" else "🧘 ${referencePoseName.ifBlank { "Practice" }}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (bodyInFrame) "👁️ body locked" else "step into frame…",
                style = MaterialTheme.typography.labelMedium,
                color = if (bodyInFrame) ToxicGreen else Color.White.copy(alpha = 0.8f),
            )
        }

        if (viewModel.isCapture) {
            CapturePanel(
                bodyInFrame = bodyInFrame,
                onCapture = { name ->
                    liveLandmarks?.let { viewModel.saveReference(name, it) { onDone() } }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            PracticePanel(
                score = score,
                bestScore = bestScore,
                holdSeconds = (holdMs / 1000L).toInt(),
                hints = hints,
                referenceMissing = reference == null,
                onFinish = {
                    viewModel.logSession(bestScore, (holdTracker.bestHoldMs / 1000L).toInt())
                    onDone()
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun CapturePanel(bodyInFrame: Boolean, onCapture: (String) -> Unit, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .frostedGlass(cornerRadius = 26.dp)
            .padding(16.dp),
    ) {
        Text(
            text = "Strike the pose, hold it, then capture — that exact frame becomes the judge.",
            style = MaterialTheme.typography.bodySmall,
            color = AuroraMuted,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Pose name — e.g. Warrior II") },
            keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuroraCyan,
                unfocusedBorderColor = AuroraMuted.copy(alpha = 0.4f),
            ),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { onCapture(name) },
            enabled = bodyInFrame && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (bodyInFrame) "📸 Capture reference" else "Waiting for a body in frame…",
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun PracticePanel(
    score: Int,
    bestScore: Int,
    holdSeconds: Int,
    hints: List<String>,
    referenceMissing: Boolean,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scoreColor = when {
        score >= 95 -> ToxicGreen
        score >= PoseMatcher.GOOD_HOLD_SCORE -> AuroraCyan
        score >= 60 -> AuroraViolet
        else -> AuroraPink
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .frostedGlass(cornerRadius = 26.dp)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (referenceMissing) {
            Text(
                text = "Reference pose unreadable — go back and teach it again.",
                style = MaterialTheme.typography.bodySmall,
                color = AuroraPink,
                textAlign = TextAlign.Center,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = scoreColor,
                )
                Text(
                    text = "/100",
                    style = MaterialTheme.typography.titleMedium,
                    color = AuroraMuted,
                    modifier = Modifier.padding(bottom = 10.dp, start = 2.dp),
                )
            }
            Text(
                text = PoseMatcher.verdict(score),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scoreColor,
            )
            Spacer(Modifier.height(6.dp))
            hints.forEach { hint ->
                Text(
                    text = "→ $hint",
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraInk,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(
                    text = "⏱ hold ${holdSeconds}s",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = AuroraCyan,
                )
                Text(
                    text = "🏆 best $bestScore",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = AuroraViolet,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text("Finish session", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun CameraPermissionPane(onGrant: () -> Unit, onBack: () -> Unit) {
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
            Text(text = "📷", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "The judge needs eyes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = AuroraInk,
            )
            Text(
                text = "Camera frames are processed on-device and never stored or uploaded — the app has no internet permission at all.",
                style = MaterialTheme.typography.bodySmall,
                color = AuroraMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onGrant) { Text("Allow camera") }
            Spacer(Modifier.height(6.dp))
            Button(onClick = onBack) { Text("Back") }
        }
    }
}
