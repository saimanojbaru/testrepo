package com.spotifymashup.generator

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.WindowInsets
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    // ── Views ────────────────────────────────────────────────────────────────
    private lateinit var rootContainer: LinearLayout
    private lateinit var etBaseUrl: EditText
    private lateinit var etTrackA: EditText
    private lateinit var etTrackB: EditText
    private lateinit var etBpmA: EditText
    private lateinit var etBpmB: EditText
    private lateinit var cbYoutubeOnly: CheckBox
    private lateinit var cbPitchShift: CheckBox

    private lateinit var btnFindHooksA: Button
    private lateinit var btnFindHooksB: Button
    private lateinit var btnGenerate: Button
    private lateinit var btnAdvToggle: TextView
    private lateinit var layoutAdvanced: LinearLayout

    private lateinit var hooksContainerA: LinearLayout
    private lateinit var hooksContainerB: LinearLayout
    private lateinit var tvHookASummary: TextView
    private lateinit var tvHookBSummary: TextView

    private lateinit var cardCompat: LinearLayout
    private lateinit var tvCompatScore: TextView
    private lateinit var tvCompatBpm: TextView
    private lateinit var tvCompatKey: TextView
    private lateinit var tvCompatEnergy: TextView
    private lateinit var tvCompatHint: TextView

    private lateinit var layoutInput: LinearLayout
    private lateinit var layoutProgress: LinearLayout
    private lateinit var layoutResult: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView

    private lateinit var tvResultFile: TextView
    private lateinit var waveform: WaveformView
    private lateinit var btnPlay: Button
    private lateinit var btnDownload: Button
    private lateinit var btnShare: Button
    private lateinit var btnReset: Button

    // ── State ────────────────────────────────────────────────────────────────
    private val mainHandler = Handler(Looper.getMainLooper())
    private var selectedHookA: ApiClient.Hook? = null
    private var selectedHookB: ApiClient.Hook? = null
    private var trackADuration: Int = 0
    private var trackBDuration: Int = 0
    private var currentJobId: String? = null
    private var currentFileName: String? = null
    private var savedUri: Uri? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playheadAnimator: Runnable? = null

    // ── Lifecycle ────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyEdgeToEdge()
        bindViews()
        wireUp()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun applyEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        window.statusBarColor = 0
        window.navigationBarColor = 0
        rootContainer = findViewById(R.id.rootContainer)
        rootContainer.setOnApplyWindowInsetsListener { v, insets ->
            val topInset: Int
            val bottomInset: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sys = insets.getInsets(WindowInsets.Type.systemBars())
                topInset = sys.top
                bottomInset = sys.bottom
            } else {
                @Suppress("DEPRECATION")
                topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottomInset = insets.systemWindowInsetBottom
            }
            v.setPadding(v.paddingLeft, v.paddingTop + topInset, v.paddingRight, v.paddingBottom + bottomInset)
            insets
        }
    }

    private fun bindViews() {
        etBaseUrl = findViewById(R.id.etBaseUrl)
        etTrackA = findViewById(R.id.etTrackA)
        etTrackB = findViewById(R.id.etTrackB)
        etBpmA = findViewById(R.id.etBpmA)
        etBpmB = findViewById(R.id.etBpmB)
        cbYoutubeOnly = findViewById(R.id.cbYoutubeOnly)
        cbPitchShift = findViewById(R.id.cbPitchShift)

        btnFindHooksA = findViewById(R.id.btnFindHooksA)
        btnFindHooksB = findViewById(R.id.btnFindHooksB)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnAdvToggle = findViewById(R.id.btnAdvToggle)
        layoutAdvanced = findViewById(R.id.layoutAdvanced)

        hooksContainerA = findViewById(R.id.hooksContainerA)
        hooksContainerB = findViewById(R.id.hooksContainerB)
        tvHookASummary = findViewById(R.id.tvHookASummary)
        tvHookBSummary = findViewById(R.id.tvHookBSummary)

        cardCompat = findViewById(R.id.cardCompat)
        tvCompatScore = findViewById(R.id.tvCompatScore)
        tvCompatBpm = findViewById(R.id.tvCompatBpm)
        tvCompatKey = findViewById(R.id.tvCompatKey)
        tvCompatEnergy = findViewById(R.id.tvCompatEnergy)
        tvCompatHint = findViewById(R.id.tvCompatHint)

        layoutInput = findViewById(R.id.layoutInput)
        layoutProgress = findViewById(R.id.layoutProgress)
        layoutResult = findViewById(R.id.layoutResult)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)

        tvResultFile = findViewById(R.id.tvResultFile)
        waveform = findViewById(R.id.waveform)
        btnPlay = findViewById(R.id.btnPlay)
        btnDownload = findViewById(R.id.btnDownload)
        btnShare = findViewById(R.id.btnShare)
        btnReset = findViewById(R.id.btnReset)
    }

    private fun wireUp() {
        btnAdvToggle.setOnClickListener {
            if (layoutAdvanced.visibility == View.GONE) {
                layoutAdvanced.visibility = View.VISIBLE
                btnAdvToggle.text = "▴  Advanced"
            } else {
                layoutAdvanced.visibility = View.GONE
                btnAdvToggle.text = "▾  Advanced"
            }
        }
        btnFindHooksA.setOnClickListener { findHooks(forA = true) }
        btnFindHooksB.setOnClickListener { findHooks(forA = false) }
        btnGenerate.setOnClickListener { startGeneration() }
        btnPlay.setOnClickListener { togglePlay() }
        btnDownload.setOnClickListener { downloadFile() }
        btnShare.setOnClickListener { shareFile() }
        btnReset.setOnClickListener { reset() }
    }

    // ── Trending hooks ───────────────────────────────────────────────────────
    private fun findHooks(forA: Boolean) {
        val track = (if (forA) etTrackA else etTrackB).text.toString().trim()
        if (track.isEmpty()) {
            toast("Paste a Spotify URL or song name first")
            return
        }
        ApiClient.baseUrl = etBaseUrl.text.toString().trim()
            .ifEmpty { "http://10.0.2.2:8000" }
        val container = if (forA) hooksContainerA else hooksContainerB
        val btn = if (forA) btnFindHooksA else btnFindHooksB

        btn.isEnabled = false
        btn.text = "Analysing…"
        container.visibility = View.GONE
        container.removeAllViews()

        thread(start = true) {
            try {
                val result = ApiClient.trendingHook(track, topK = 4)
                mainHandler.post {
                    if (forA) trackADuration = result.durationMs else trackBDuration = result.durationMs
                    renderHooks(container, result, forA)
                    btn.text = "🔥  Find viral hooks"
                    btn.isEnabled = true
                    fadeInDownAnimation(container)
                    container.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                mainHandler.post {
                    toast("Couldn't analyse: ${e.message?.take(120)}")
                    btn.text = "🔥  Find viral hooks"
                    btn.isEnabled = true
                }
            }
        }
    }

    private fun renderHooks(
        container: LinearLayout,
        data: ApiClient.TrendingHooks,
        forA: Boolean,
    ) {
        val headerView = TextView(this).apply {
            text = "“${data.trackName}” · ${data.artistName}"
            textSize = 13f
            setPadding(2, 2, 2, 8)
            setTextColor(getColor(R.color.text_primary))
        }
        container.addView(headerView)

        for ((idx, h) in data.hooks.withIndex()) {
            val card = layoutInflater.inflate(R.layout.card_hook, container, false)
            val tvLabel = card.findViewById<TextView>(R.id.tvLabel)
            val tvTime = card.findViewById<TextView>(R.id.tvTime)
            val tvScore = card.findViewById<TextView>(R.id.tvScore)
            val tvReasons = card.findViewById<TextView>(R.id.tvReasons)
            val miniWave = card.findViewById<MiniWaveformView>(R.id.miniWave)
            val btnSelect = card.findViewById<Button>(R.id.btnSelect)
            val btnPreview = card.findViewById<Button>(R.id.btnPreview)

            tvLabel.text = h.label.uppercase()
            tvTime.text = "${formatMs(h.startMs)}–${formatMs(h.endMs)} · ${(h.durationMs / 1000)}s"
            tvScore.text = "${h.score.toInt()}%"
            tvReasons.text = h.reasons.joinToString("  ·  ")
            miniWave.setSeed(idx + h.startMs)

            btnSelect.setOnClickListener {
                if (forA) {
                    selectedHookA = h
                    tvHookASummary.visibility = View.VISIBLE
                    tvHookASummary.text = "✓ ${h.label} ${formatMs(h.startMs)}"
                } else {
                    selectedHookB = h
                    tvHookBSummary.visibility = View.VISIBLE
                    tvHookBSummary.text = "✓ ${h.label} ${formatMs(h.startMs)}"
                }
                for (i in 0 until container.childCount) {
                    container.getChildAt(i).alpha = 0.5f
                }
                card.alpha = 1f
                btnSelect.text = getString(R.string.hook_selected)
                if (selectedHookA != null && selectedHookB != null) {
                    runCompatibilityCheck()
                }
            }

            btnPreview.setOnClickListener {
                toast("Preview will play once a mashup is generated")
            }

            container.addView(card)
        }
    }

    // ── Compatibility ───────────────────────────────────────────────────────
    private fun runCompatibilityCheck() {
        val a = etTrackA.text.toString().trim()
        val b = etTrackB.text.toString().trim()
        if (a.isEmpty() || b.isEmpty()) return
        cardCompat.visibility = View.VISIBLE
        tvCompatScore.text = "…"
        tvCompatBpm.text = "—"
        tvCompatKey.text = "—"
        tvCompatEnergy.text = "—"
        tvCompatHint.text = "Comparing tempos, keys, and energy…"

        thread(start = true) {
            try {
                val c = ApiClient.compatibility(a, b)
                mainHandler.post { showCompat(c) }
            } catch (e: Exception) {
                mainHandler.post {
                    tvCompatScore.text = "?"
                    tvCompatHint.text = "Compatibility check unavailable: ${e.message?.take(100)}"
                }
            }
        }
    }

    private fun showCompat(c: ApiClient.Compatibility) {
        val pct = (c.overallScore * 100).toInt()
        tvCompatScore.text = "$pct%"
        tvCompatBpm.text = if (c.bpmA != null && c.bpmB != null) {
            "${c.bpmA.toInt()} ↔ ${c.bpmB.toInt()}"
        } else "—"
        tvCompatKey.text = listOfNotNull(c.keyALabel, c.keyBLabel)
            .joinToString(" ↔ ").ifEmpty { "—" }
        tvCompatEnergy.text = "${(c.energyScore * 100).toInt()}%"
        val advice = StringBuilder()
        if (c.suggestedTempoRatio != null) {
            val pct2 = ((c.suggestedTempoRatio - 1f) * 100).toInt()
            if (pct2 != 0) advice.append("Stretch A by ${if (pct2 > 0) "+" else ""}$pct2%. ")
        }
        if (c.suggestedPitchShift != null && c.suggestedPitchShift != 0) {
            advice.append("Pitch-shift A by ${c.suggestedPitchShift} semitones for key match. ")
        }
        if (advice.isEmpty() && pct >= 70) advice.append("Strong match — minimal correction needed.")
        if (advice.isEmpty() && pct < 70) advice.append("Manual BPM/key may improve the result.")
        tvCompatHint.text = advice.toString()
    }

    // ── Generate ─────────────────────────────────────────────────────────────
    private fun startGeneration() {
        val baseUrl = etBaseUrl.text.toString().trim().ifEmpty { "http://10.0.2.2:8000" }
        val trackA = etTrackA.text.toString().trim()
        val trackB = etTrackB.text.toString().trim()
        val youtubeOnly = cbYoutubeOnly.isChecked
        val bpmA = etBpmA.text.toString().toDoubleOrNull()
        val bpmB = etBpmB.text.toString().toDoubleOrNull()
        val pitchShift = cbPitchShift.isChecked

        if (trackA.isEmpty() || trackB.isEmpty()) {
            toast("Please fill in both track fields"); return
        }
        if (youtubeOnly && bpmB == null) {
            toast("BPM B is required in YouTube-only mode"); return
        }

        ApiClient.baseUrl = baseUrl
        currentFileName = buildFileName(trackA, trackB)
        setSection(Section.PROGRESS)
        updateProgress("Connecting to backend…", 0)
        btnGenerate.isEnabled = false

        thread(start = true) {
            try {
                val job = ApiClient.createMashup(
                    trackA, trackB,
                    youtubeOnly, bpmA, bpmB, pitchShift,
                    selectedHookA, selectedHookB,
                )
                currentJobId = job.jobId
                pollLoop(job.jobId)
            } catch (e: Exception) {
                mainHandler.post { showError("Connection failed: ${e.message}") }
            }
        }
    }

    private fun pollLoop(jobId: String) {
        while (true) {
            try {
                val job = ApiClient.getJob(jobId)
                mainHandler.post {
                    when (job.status) {
                        "done" -> showResult()
                        "failed" -> showError(job.message)
                        else -> updateProgress(job.message, job.progress)
                    }
                }
                if (job.status == "done" || job.status == "failed") break
                Thread.sleep(3_000)
            } catch (e: Exception) {
                mainHandler.post { showError("Network error: ${e.message}") }
                break
            }
        }
    }

    // ── Playback ─────────────────────────────────────────────────────────────
    private fun togglePlay() {
        val jobId = currentJobId ?: return
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            btnPlay.text = getString(R.string.play_preview)
            stopPlayheadAnimation()
            return
        }
        if (mediaPlayer != null) {
            mediaPlayer?.start()
            btnPlay.text = getString(R.string.pause)
            startPlayheadAnimation()
            return
        }

        btnPlay.isEnabled = false
        btnPlay.text = "Buffering…"

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(ApiClient.downloadUrl(jobId))
            setOnPreparedListener { mp ->
                mp.start()
                mainHandler.post {
                    btnPlay.isEnabled = true
                    btnPlay.text = getString(R.string.pause)
                    startPlayheadAnimation()
                }
            }
            setOnCompletionListener {
                mainHandler.post {
                    btnPlay.text = getString(R.string.play_preview)
                    stopPlayheadAnimation()
                    waveform.playheadFraction = 0f
                }
            }
            setOnErrorListener { _, _, _ ->
                mainHandler.post {
                    toast("Playback failed — try Save MP3 instead")
                    btnPlay.text = getString(R.string.play_preview)
                    btnPlay.isEnabled = true
                }
                releasePlayer()
                false
            }
            prepareAsync()
        }

        waveform.onSeek = { frac ->
            mediaPlayer?.let { mp ->
                if (mp.duration > 0) mp.seekTo((frac * mp.duration).toInt())
            }
        }
    }

    private fun startPlayheadAnimation() {
        stopPlayheadAnimation()
        playheadAnimator = object : Runnable {
            override fun run() {
                val mp = mediaPlayer ?: return
                if (mp.duration > 0 && mp.isPlaying) {
                    waveform.playheadFraction = mp.currentPosition.toFloat() / mp.duration
                }
                mainHandler.postDelayed(this, 50L)
            }
        }
        mainHandler.post(playheadAnimator!!)
    }

    private fun stopPlayheadAnimation() {
        playheadAnimator?.let { mainHandler.removeCallbacks(it) }
        playheadAnimator = null
    }

    // ── Save / Share ─────────────────────────────────────────────────────────
    private fun downloadFile() {
        val jobId = currentJobId ?: return
        val fileName = currentFileName ?: return

        btnDownload.isEnabled = false
        btnDownload.text = "Saving…"

        thread(start = true) {
            try {
                val url = URL(ApiClient.downloadUrl(jobId))
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 120_000
                }
                val uri = saveToMusic(fileName, conn) ?: error("Failed to create destination")
                savedUri = uri
                mainHandler.post {
                    btnDownload.text = "✓  Saved"
                    toast("Saved to Music/Mashups")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    btnDownload.isEnabled = true
                    btnDownload.text = getString(R.string.save_mp3)
                    toast("Download failed: ${e.message}")
                }
            }
        }
    }

    private fun saveToMusic(fileName: String, conn: HttpURLConnection): Uri? {
        val resolver = contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Mashups")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
            resolver.openOutputStream(uri)?.use { out: OutputStream ->
                conn.inputStream.use { it.copyTo(out) }
            }
            cv.clear()
            cv.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, cv, null, null)
            uri
        } else {
            @Suppress("DEPRECATION")
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val dir = File(musicDir, "Mashups").apply { mkdirs() }
            val file = File(dir, fileName)
            file.outputStream().use { out -> conn.inputStream.use { it.copyTo(out) } }
            Uri.fromFile(file)
        }
    }

    private fun shareFile() {
        val uri = savedUri ?: run {
            toast("Save the file first")
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share mashup"))
    }

    // ── State machine ────────────────────────────────────────────────────────
    private enum class Section { INPUT, PROGRESS, RESULT }

    private fun setSection(s: Section) {
        layoutInput.visibility = if (s == Section.INPUT) View.VISIBLE else View.GONE
        layoutProgress.visibility = if (s == Section.PROGRESS) View.VISIBLE else View.GONE
        layoutResult.visibility = if (s == Section.RESULT) View.VISIBLE else View.GONE
        if (s == Section.RESULT) fadeInDownAnimation(layoutResult)
        if (s == Section.PROGRESS) fadeInDownAnimation(layoutProgress)
    }

    private fun updateProgress(msg: String, pct: Int) {
        tvProgress.text = msg
        progressBar.progress = pct
    }

    private fun showResult() {
        setSection(Section.RESULT)
        tvResultFile.text = currentFileName ?: "mashup.mp3"
        val total = maxOf(trackADuration, trackBDuration, 1)
        selectedHookA?.let {
            waveform.setHookRegion(it.startMs.toFloat() / total, it.endMs.toFloat() / total)
        }
        btnPlay.isEnabled = true
        btnPlay.text = getString(R.string.play_preview)
        btnDownload.isEnabled = true
        btnDownload.text = getString(R.string.save_mp3)
        releasePlayer()
    }

    private fun showError(msg: String) {
        setSection(Section.INPUT)
        btnGenerate.isEnabled = true
        toast("Error: $msg")
    }

    private fun reset() {
        releasePlayer()
        setSection(Section.INPUT)
        btnGenerate.isEnabled = true
        currentJobId = null
        currentFileName = null
        savedUri = null
        selectedHookA = null
        selectedHookB = null
        hooksContainerA.removeAllViews()
        hooksContainerB.removeAllViews()
        hooksContainerA.visibility = View.GONE
        hooksContainerB.visibility = View.GONE
        tvHookASummary.visibility = View.GONE
        tvHookBSummary.visibility = View.GONE
        cardCompat.visibility = View.GONE
    }

    private fun releasePlayer() {
        stopPlayheadAnimation()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun buildFileName(a: String, b: String): String {
        fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9 ]"), "").trim().replace(' ', '_').take(28)
        return "Mashup_${safe(a)}_vs_${safe(b)}.mp3"
    }

    private fun formatMs(ms: Int): String {
        val s = ms / 1000
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    private fun fadeInDownAnimation(v: View) {
        val anim = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 220 })
            addAnimation(TranslateAnimation(0f, 0f, 30f, 0f).apply { duration = 260 })
        }
        v.startAnimation(anim)
    }
}
