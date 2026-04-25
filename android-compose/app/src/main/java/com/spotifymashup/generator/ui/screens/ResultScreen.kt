package com.spotifymashup.generator.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.spotifymashup.generator.ui.components.Waveform
import com.spotifymashup.generator.viewmodel.MashupViewModel
import kotlinx.coroutines.delay

@Composable
fun ResultScreen(vm: MashupViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(vm.downloadUrl()))
            prepare()
        }
    }
    var playing by remember { mutableStateOf(false) }
    var head by remember { mutableFloatStateOf(0f) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    androidx.compose.runtime.LaunchedEffect(playing) {
        while (playing) {
            val d = player.duration.coerceAtLeast(1L)
            head = (player.currentPosition.toFloat() / d).coerceIn(0f, 1f)
            delay(50L)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Your mashup is ready",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                state.resultFilename ?: "mashup.mp3",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )

            val hookA = state.trackA.selectedHook
            val totalMs = maxOf(state.trackA.durationMs, state.trackB.durationMs, 1)
            Waveform(
                playheadFraction = head,
                hookStart = hookA?.startMs?.toFloat()?.div(totalMs) ?: -1f,
                hookEnd = hookA?.endMs?.toFloat()?.div(totalMs) ?: -1f,
                onSeek = { f ->
                    val target = (f * player.duration).toLong()
                    player.seekTo(target)
                    head = f
                },
            )

            Spacer(Modifier.height(20.dp))

            Button(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                onClick = {
                    if (player.isPlaying) { player.pause(); playing = false }
                    else { player.play(); playing = true }
                },
            ) { Text(if (playing) "⏸  Pause" else "▶  Play preview") }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f).height(50.dp),
                    onClick = { /* save: implement using MediaStore */ },
                ) { Text("⬇  Save MP3") }
                OutlinedButton(
                    modifier = Modifier.weight(1f).height(50.dp),
                    onClick = { /* share intent */ },
                ) { Text("⤴  Share") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { vm.reset() }) { Text("Start over") }
        }
    }
}
