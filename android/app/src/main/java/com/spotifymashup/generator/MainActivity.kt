package com.spotifymashup.generator

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.spotifymashup.generator.BuildConfig
import com.spotifymashup.generator.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MashupViewModel by viewModels()
    private var mediaPlayer: MediaPlayer? = null

    // Current completed job (for streaming preview before saving)
    private var currentJobId: String? = null
    private var currentFileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStemBackendSpinner()
        setupClickListeners()
        observeState()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    // ── UI setup ──────────────────────────────────────────────────────────────

    private fun setupStemBackendSpinner() {
        val options = listOf("demucs (recommended)", "spleeter (faster)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerStemBackend.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnGenerate.setOnClickListener { onGenerateClicked() }
        binding.btnReset.setOnClickListener { viewModel.reset() }
        binding.btnDownload.setOnClickListener { onDownloadClicked() }
        binding.btnPlay.setOnClickListener { onPlayClicked() }

        // Show/hide advanced options
        binding.tvAdvancedToggle.setOnClickListener {
            val visible = !binding.layoutAdvanced.isVisible
            binding.layoutAdvanced.isVisible = visible
            binding.tvAdvancedToggle.text = if (visible) "Hide advanced options ▲" else "Show advanced options ▼"
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun onGenerateClicked() {
        val trackA = binding.etTrackA.text.toString().trim()
        val trackB = binding.etTrackB.text.toString().trim()

        if (trackA.isEmpty() || trackB.isEmpty()) {
            Toast.makeText(this, "Please fill in both track fields", Toast.LENGTH_SHORT).show()
            return
        }

        val youtubeOnly = binding.switchYoutubeOnly.isChecked
        val bpmA = binding.etBpmA.text.toString().toFloatOrNull()
        val bpmB = binding.etBpmB.text.toString().toFloatOrNull()
        val keyA = binding.etKeyA.text.toString().toIntOrNull()
        val keyB = binding.etKeyB.text.toString().toIntOrNull()
        val pitchShift = binding.switchPitchShift.isChecked
        val backend = if (binding.spinnerStemBackend.selectedItemPosition == 0) "demucs" else "spleeter"

        if (youtubeOnly && bpmB == null) {
            Toast.makeText(this, "BPM B is required in YouTube-only mode", Toast.LENGTH_LONG).show()
            return
        }

        viewModel.generateMashup(
            trackA = trackA,
            trackB = trackB,
            youtubeOnly = youtubeOnly,
            bpmA = bpmA,
            bpmB = bpmB,
            keyA = keyA,
            keyB = keyB,
            applyPitchShift = pitchShift,
            stemBackend = backend,
        )
    }

    private fun onDownloadClicked() {
        val jobId = currentJobId ?: return
        val fileName = currentFileName ?: return
        viewModel.downloadMashup(this, jobId, fileName)
    }

    private fun onPlayClicked() {
        val jobId = currentJobId ?: return

        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            binding.btnPlay.text = getString(R.string.play)
            return
        }

        if (mediaPlayer != null) {
            mediaPlayer?.start()
            binding.btnPlay.text = getString(R.string.pause)
            return
        }

        // First play — stream from server
        val streamUrl = "${BuildConfig.BASE_URL}/api/mashup/$jobId/download"
        binding.btnPlay.isEnabled = false
        binding.btnPlay.text = getString(R.string.buffering)

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(streamUrl)
            setOnPreparedListener { mp ->
                mp.start()
                binding.btnPlay.isEnabled = true
                binding.btnPlay.text = getString(R.string.pause)
            }
            setOnCompletionListener {
                binding.btnPlay.text = getString(R.string.play)
            }
            setOnErrorListener { _, _, _ ->
                Toast.makeText(this@MainActivity, "Playback error — try downloading instead", Toast.LENGTH_LONG).show()
                binding.btnPlay.text = getString(R.string.play)
                binding.btnPlay.isEnabled = true
                releasePlayer()
                false
            }
            prepareAsync()
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // ── State observation ─────────────────────────────────────────────────────

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is MashupUiState.Idle -> showInputSection()

                    is MashupUiState.Loading -> {
                        showProgressSection()
                        binding.progressBar.progress = state.progress
                        binding.tvProgressMessage.text = state.message
                    }

                    is MashupUiState.Ready -> {
                        currentJobId = state.jobId
                        currentFileName = state.fileName
                        releasePlayer()
                        showResultSection(state.fileName)
                    }

                    is MashupUiState.Downloading -> {
                        showProgressSection()
                        binding.progressBar.progress = 0
                        binding.progressBar.isIndeterminate = true
                        binding.tvProgressMessage.text = "Saving ${state.fileName} to Music/Mashups…"
                    }

                    is MashupUiState.Downloaded -> {
                        binding.progressBar.isIndeterminate = false
                        val path = state.savedPath
                        Toast.makeText(this@MainActivity, "Saved: $path", Toast.LENGTH_LONG).show()
                        // Stay on result section so user can play again
                        showResultSection(currentFileName ?: "mashup.mp3")
                        binding.btnDownload.text = getString(R.string.saved)
                        binding.btnDownload.isEnabled = false
                    }

                    is MashupUiState.Error -> {
                        showInputSection()
                        Toast.makeText(this@MainActivity, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // ── Section visibility helpers ────────────────────────────────────────────

    private fun showInputSection() {
        binding.layoutInput.isVisible = true
        binding.layoutProgress.isVisible = false
        binding.layoutResult.isVisible = false
        binding.progressBar.isIndeterminate = false
        binding.btnDownload.isEnabled = true
        binding.btnDownload.text = getString(R.string.download_mp3)
        binding.btnPlay.text = getString(R.string.play)
        releasePlayer()
    }

    private fun showProgressSection() {
        binding.layoutInput.isVisible = false
        binding.layoutProgress.isVisible = true
        binding.layoutResult.isVisible = false
    }

    private fun showResultSection(fileName: String) {
        binding.layoutInput.isVisible = false
        binding.layoutProgress.isVisible = false
        binding.layoutResult.isVisible = true
        binding.tvResultTitle.text = fileName
    }
}
