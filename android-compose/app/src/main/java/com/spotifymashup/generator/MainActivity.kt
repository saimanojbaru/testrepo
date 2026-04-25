package com.spotifymashup.generator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotifymashup.generator.ui.screens.HomeScreen
import com.spotifymashup.generator.ui.screens.ProgressScreen
import com.spotifymashup.generator.ui.screens.ResultScreen
import com.spotifymashup.generator.ui.theme.MashupTheme
import com.spotifymashup.generator.viewmodel.MashupViewModel
import android.widget.Toast

class MainActivity : ComponentActivity() {

    private val viewModel: MashupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MashupTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MashupApp(viewModel)
                }
            }
        }
    }
}

@Composable
private fun MashupApp(vm: MashupViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LaunchedEffect(state.toast) {
        state.toast?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
            vm.clearToast()
        }
    }

    AnimatedContent(
        targetState = state.stage,
        transitionSpec = {
            (fadeIn() + slideInVertically { it / 8 }) togetherWith
                (fadeOut() + slideOutVertically { -it / 8 })
        },
        label = "stage",
    ) { stage ->
        when (stage) {
            com.spotifymashup.generator.viewmodel.Stage.Input -> HomeScreen(vm)
            com.spotifymashup.generator.viewmodel.Stage.Progress -> ProgressScreen(vm)
            com.spotifymashup.generator.viewmodel.Stage.Result -> ResultScreen(vm)
        }
    }
}
