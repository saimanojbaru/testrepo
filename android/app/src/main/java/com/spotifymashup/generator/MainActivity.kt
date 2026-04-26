package com.spotifymashup.generator

import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

enum class Section { Input, Progress, Result }

data class TrackCardState(
    var url: String = "",
    var title: String = "",
    var artist: String = "",
    var durationMs: Int = 0,
    var selectedHook: ApiClient.Hook? = null,
    var role: String = "full",
)

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var mainHandler: Handler
    private lateinit var layoutInput: View
    private lateinit var layoutProgress: View
    private lateinit var layoutResult: View
    private lateinit var llTracks: LinearLayout
    private lateinit var btnAddTrack: TextView
    private lateinit var cardCompat: View
    private lateinit var btnGenerate: Button
    private lateinit var btnAdvToggle: TextView
    private lateinit var layoutAdvanced: View
    private lateinit var btnSyncToggle: TextView
    private lateinit var llSyncControls: View
    private lateinit var manualSyncView: ManualSyncView
    private lateinit var etBaseUrl: EditText
    private lateinit var tvProgress: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvResultFile: TextView
    private lateinit var waveform: WaveformView
    private lateinit var btnPlay: Button
    private lateinit var btnDownload: Button
    private lateinit var btnShare: Button
    private lateinit var btnReset: Button

    private val tracks = mutableListOf<TrackCardState>()
    private var currentJobId: String? = null
    private var currentFileName: String? = null
    private var savedUri: Uri? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playheadAnimator: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("mashup_prefs", MODE_PRIVATE)
        mainHandler = Handler(Looper.getMainLooper())

        applyEdgeToEdge()
        bindViews()

        // First-launch setup dialog
        if (!prefs.contains("backend_url")) {
            showSetupDialog()
        } else {
            ApiClient.baseUrl = prefs.getString("backend_url", "") ?: ""
        }

        // Initialize with 2 empty tracks
        addTrack("Vocals")
        addTrack("Instrumental")
        updateAddTrackButton()

        wireUp()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun applyEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 29) {
            window.decorView.windowInsetsController?.hide(
                WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars()
            ) ?: run {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
        }
    }

    private fun bindViews() {
        layoutInput = findViewById(R.id.layoutInput)
        layoutProgress = findViewById(R.id.layoutProgress)
        layoutResult = findViewById(R.id.layoutResult)
        llTracks = findViewById(R.id.llTracks)
        btnAddTrack = findViewById(R.id.btnAddTrack)
        cardCompat = findViewById(R.id.cardCompat)
        btnGenerate = findViewById(R.id.btnGenerate)
        btnAdvToggle = findViewById(R.id.btnAdvToggle)
        layoutAdvanced = findViewById(R.id.layoutAdvanced)
        btnSyncToggle = findViewById(R.id.btnSyncToggle)
        llSyncControls = findViewById(R.id.llSyncControls)
        manualSyncView = findViewById(R.id.manualSyncView)
        etBaseUrl = findViewById(R.id.etBaseUrl)
        tvProgress = findViewById(R.id.tvProgress)
        progressBar = findViewById(R.id.progressBar)
        tvResultFile = findViewById(R.id.tvResultFile)
        waveform = findViewById(R.id.waveform)
        btnPlay = findViewById(R.id.btnPlay)
        btnDownload = findViewById(R.id.btnDownload)
        btnShare = findViewById(R.id.btnShare)
        btnReset = findViewById(R.id.btnReset)

        etBaseUrl.setText(ApiClient.baseUrl)
    }

    private fun wireUp() {
        btnAddTrack.setOnClickListener { if (tracks.size < 4) addTrack("Track ${tracks.size + 1}") }
        btnAdvToggle.setOnClickListener { toggleSection(layoutAdvanced) }
        btnSyncToggle.setOnClickListener { toggleSection(llSyncControls) }
        findViewById<TextView>(R.id.btnChangeServer).setOnClickListener { showSetupDialog() }
        btnGenerate.setOnClickListener { startGeneration() }
        btnPlay.setOnClickListener { togglePlay() }
        btnDownload.setOnClickListener { downloadFile() }
        btnShare.setOnClickListener { shareFile() }
        btnReset.setOnClickListener { reset() }
    }

    private fun toggleSection(v: View) {
        v.visibility = if (v.visibility == View.GONE) View.VISIBLE else View.GONE
    }

    private fun addTrack(role: String) {
        val card = layoutInflater.inflate(R.layout.card_track, llTracks, false)
        val state = TrackCardState(role = role)
        val idx = tracks.size
        tracks.add(state)

        card.findViewById<TextView>(R.id.tvTrackLabel).text = "TRACK ${idx + 1}"
        val spRole = card.findViewById<Spinner>(R.id.spRole)
        val roles = arrayOf("Vocals", "Instrumental", "Drums", "Melody", "Full")
        spRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spRole.setSelection(if (role == "Vocals") 0 else if (role == "Instrumental") 1 else 4)
        spRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { state.role = roles[p2].lowercase() }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        card.findViewById<TextView>(R.id.btnRemoveTrack).apply {
            if (tracks.size > 2) {
                visibility = View.VISIBLE
                setOnClickListener { removeTrack(idx) }
            }
        }

        val etSearch = card.findViewById<EditText>(R.id.etSearch)
        val spSource = card.findViewById<Spinner>(R.id.spSource)
        spSource.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("YouTube")).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        card.findViewById<TextView>(R.id.btnSearch).setOnClickListener {
            val q = etSearch.text.toString().trim()
            if (q.isNotEmpty()) searchTracks(idx, q, "youtube")
        }

        card.findViewById<TextView>(R.id.btnFindHooks).setOnClickListener { findHooks(idx) }

        llTracks.addView(card)
        updateAddTrackButton()
    }

    private fun removeTrack(idx: Int) {
        if (tracks.size <= 2) return
        tracks.removeAt(idx)
        llTracks.removeViewAt(idx)
        updateAddTrackButton()
    }

    private fun updateAddTrackButton() {
        btnAddTrack.visibility = if (tracks.size < 4) View.VISIBLE else View.GONE
    }

    private fun searchTracks(trackIdx: Int, query: String, source: String) {
        val card = llTracks.getChildAt(trackIdx)
        val resultsContainer = card.findViewById<LinearLayout>(R.id.llSearchResults)
        resultsContainer.removeAllViews()
        resultsContainer.visibility = View.GONE

        thread {
            try {
                val results = ApiClient.search(query, source, 5)
                mainHandler.post {
                    resultsContainer.visibility = View.VISIBLE
                    results.forEach { r ->
                        val item = layoutInflater.inflate(R.layout.item_search_result, resultsContainer, false)
                        item.findViewById<TextView>(R.id.tvSourceBadge).text = if (r.source == "spotify") "SP" else "YT"
                        item.findViewById<TextView>(R.id.tvResultTitle).text = r.title
                        item.findViewById<TextView>(R.id.tvResultArtist).text = r.artist
                        item.findViewById<TextView>(R.id.tvResultDuration).text = "${r.durationMs / 60000}:${(r.durationMs % 60000) / 1000}"
                        // Instant preview — launches YouTube directly
                        item.findViewById<TextView>(R.id.btnResultPreview).setOnClickListener {
                            previewAtTime(r.url, 0)
                        }
                        item.findViewById<TextView>(R.id.btnResultSelect).setOnClickListener {
                            tracks[trackIdx].url = r.url
                            tracks[trackIdx].title = r.title
                            tracks[trackIdx].artist = r.artist
                            tracks[trackIdx].durationMs = r.durationMs
                            showSelectedTrack(trackIdx)
                            resultsContainer.visibility = View.GONE
                        }
                        resultsContainer.addView(item, 0)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post { toast("Search failed: ${e.message?.take(80)}") }
            }
        }
    }

    private fun showSelectedTrack(idx: Int) {
        val card = llTracks.getChildAt(idx)
        val t = tracks[idx]
        card.findViewById<LinearLayout>(R.id.llSelected).visibility = View.VISIBLE
        card.findViewById<TextView>(R.id.tvSelectedTitle).text = t.title
        card.findViewById<TextView>(R.id.tvSelectedArtist).text = t.artist
        card.findViewById<TextView>(R.id.tvSelectedDuration).text = "${t.durationMs / 60000}:${(t.durationMs % 60000) / 1000}"
    }

    private fun findHooks(idx: Int) {
        val t = tracks[idx]
        if (t.url.isEmpty()) { toast("Select a track first"); return }

        val card = llTracks.getChildAt(idx)
        val hooksContainer = card.findViewById<LinearLayout>(R.id.llHooks)
        hooksContainer.removeAllViews()

        card.findViewById<TextView>(R.id.tvHookSummary).visibility = View.GONE
        val btn = card.findViewById<Button>(R.id.btnFindHooks)
        btn.isEnabled = false
        btn.text = "Analysing…"

        thread {
            try {
                val hooks = ApiClient.trendingHook(t.url, 4).hooks
                mainHandler.post {
                    hooks.forEach { h ->
                        val hookCard = layoutInflater.inflate(R.layout.card_hook, hooksContainer, false)
                        hookCard.findViewById<TextView>(R.id.tvLabel).text = h.label.uppercase()
                        hookCard.findViewById<TextView>(R.id.tvTime).text = "${formatMs(h.startMs)}–${formatMs(h.endMs)} · ${h.durationMs / 1000}s"
                        hookCard.findViewById<TextView>(R.id.tvScore).text = "${h.score.toInt()}%"
                        hookCard.findViewById<TextView>(R.id.tvReasons).text = h.reasons.joinToString("  ·  ")
                        hookCard.findViewById<Button>(R.id.btnSelect).setOnClickListener {
                            t.selectedHook = h
                            card.findViewById<TextView>(R.id.tvHookSummary).apply {
                                visibility = View.VISIBLE
                                text = "✓ ${h.label} ${formatMs(h.startMs)}"
                            }
                        }
                        // Quick preview: launch YouTube/browser at the hook timestamp — instant
                        hookCard.findViewById<Button>(R.id.btnPreview).setOnClickListener {
                            previewAtTime(t.url, h.startMs / 1000)
                        }
                        hooksContainer.addView(hookCard)
                    }
                    btn.text = "🔥  Find viral hooks"
                    btn.isEnabled = true
                }
            } catch (e: Exception) {
                mainHandler.post { toast("Hook analysis failed: ${e.message?.take(80)}") }
                btn.text = "🔥  Find viral hooks"
                btn.isEnabled = true
            }
        }
    }

    private fun startGeneration() {
        if (tracks.any { it.url.isEmpty() }) { toast("Fill in all tracks first"); return }
        if (ApiClient.baseUrl.isEmpty()) { toast("Enter backend URL first"); return }

        setSection(Section.Progress)
        updateProgress("Connecting to backend…", 0)
        btnGenerate.isEnabled = false

        val trackInputs = tracks.map { t ->
            ApiClient.TrackInput(
                url = t.url,
                role = t.role,
                hookStartMs = t.selectedHook?.startMs,
                hookEndMs = t.selectedHook?.endMs,
            )
        }

        thread {
            try {
                val job = ApiClient.createMultiMashup(trackInputs, applyPitchShift = true)
                currentJobId = job.jobId
                currentFileName = buildFileName(tracks.map { it.title })
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
                mainHandler.post { updateProgress(job.message, job.progress) }
                when (job.status) {
                    "done" -> { mainHandler.post { showResult() }; break }
                    "failed" -> { mainHandler.post { showError(job.message) }; break }
                    else -> Thread.sleep(2000)
                }
            } catch (e: Exception) {
                mainHandler.post { showError("Poll failed: ${e.message}") }
                break
            }
        }
    }

    private fun togglePlay() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(ApiClient.downloadUrl(currentJobId ?: ""))
                prepareAsync()
                setOnPreparedListener { start(); btnPlay.text = "⏸  Pause" }
            }
        } else {
            if (mediaPlayer!!.isPlaying) { mediaPlayer!!.pause(); btnPlay.text = "▶  Play preview" }
            else { mediaPlayer!!.start(); btnPlay.text = "⏸  Pause" }
        }
    }

    private fun downloadFile() {
        saveToMusic(currentFileName ?: "mashup.mp3")?.let { savedUri = it }
    }

    private fun saveToMusic(fileName: String): Uri? {
        return try {
            val conn = (URL(ApiClient.downloadUrl(currentJobId ?: "")).openConnection() as HttpURLConnection).apply { connect() }
            if (conn.responseCode !in 200..299) return null

            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { conn.inputStream.copyTo(it) }
                    cv.clear()
                    cv.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    contentResolver.update(uri, cv, null, null)
                    uri
                } else null
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                dir.mkdirs()
                val file = File(dir, fileName)
                file.outputStream().use { conn.inputStream.copyTo(it) }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun shareFile() {
        if (savedUri == null) { toast("Download first"); return }
        startActivity(Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, savedUri)
        })
    }

    private fun setSection(s: Section) {
        layoutInput.visibility = if (s == Section.Input) View.VISIBLE else View.GONE
        layoutProgress.visibility = if (s == Section.Progress) View.VISIBLE else View.GONE
        layoutResult.visibility = if (s == Section.Result) View.VISIBLE else View.GONE
    }

    private fun updateProgress(msg: String, pct: Int) { tvProgress.text = msg; progressBar.progress = pct }

    private fun showResult() { setSection(Section.Result) }

    private fun showError(msg: String) { setSection(Section.Input); toast("Error: ${msg.take(120)}") }

    private fun reset() { setSection(Section.Input); mediaPlayer?.release(); mediaPlayer = null; btnPlay.text = "▶  Play preview" }

    private fun releasePlayer() { mediaPlayer?.release(); mediaPlayer = null }

    private fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }

    /** Open YouTube (or browser) at the given second — instant preview, no streaming wait. */
    private fun previewAtTime(url: String, startSeconds: Int) {
        try {
            val previewUrl = if (url.contains("youtu")) {
                if (url.contains("?")) "$url&t=${startSeconds}s" else "$url?t=${startSeconds}s"
            } else url
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl)))
        } catch (e: Exception) {
            toast("Couldn't open preview")
        }
    }

    private fun buildFileName(titles: List<String>) = titles.take(2).joinToString(" + ").replace(Regex("[^A-Za-z0-9 +]"), "").take(40) + ".mp3"

    private fun formatMs(ms: Int): String = "${ms / 60000}:${(ms % 60000) / 1000}".replace(Regex(":(\\d)$"), ":0$1")

    private fun showSetupDialog() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar).apply {
            setContentView(layoutInflater.inflate(R.layout.dialog_setup, null))
            setCancelable(false)
            window?.setBackgroundDrawable(getDrawable(android.R.color.transparent))
        }
        dialog.findViewById<EditText>(R.id.etSetupUrl).setText(ApiClient.baseUrl)
        dialog.findViewById<Button>(R.id.btnSetupConnect).setOnClickListener {
            val url = dialog.findViewById<EditText>(R.id.etSetupUrl).text.toString().trim()
            if (url.isEmpty()) { toast("Enter a URL"); return@setOnClickListener }
            dialog.findViewById<TextView>(R.id.tvSetupStatus).apply { visibility = View.VISIBLE; text = "Testing…" }
            thread {
                try {
                    ApiClient.baseUrl = url
                    if (ApiClient.health()) {
                        prefs.edit().putString("backend_url", url).apply()
                        mainHandler.post { dialog.dismiss() }
                    } else {
                        mainHandler.post { dialog.findViewById<TextView>(R.id.tvSetupStatus).text = "Connection failed. Check URL." }
                    }
                } catch (e: Exception) {
                    mainHandler.post { dialog.findViewById<TextView>(R.id.tvSetupStatus).text = "Error: ${e.message?.take(40)}" }
                }
            }
        }
        dialog.show()
    }
}
