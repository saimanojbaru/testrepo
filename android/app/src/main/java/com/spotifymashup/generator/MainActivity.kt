package com.spotifymashup.generator

import android.app.Activity
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import android.media.MediaScannerConnection
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var etBaseUrl: EditText
    private lateinit var etTrackA: EditText
    private lateinit var etTrackB: EditText
    private lateinit var cbYoutubeOnly: CheckBox
    private lateinit var etBpmA: EditText
    private lateinit var etBpmB: EditText
    private lateinit var cbPitchShift: CheckBox
    private lateinit var btnGenerate: Button
    private lateinit var layoutInput: LinearLayout
    private lateinit var layoutProgress: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var layoutResult: LinearLayout
    private lateinit var tvResultFile: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnDownload: Button
    private lateinit var btnReset: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentJobId: String? = null
    private var currentFileName: String? = null
    private var mediaPlayer: MediaPlayer? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etBaseUrl      = findViewById(R.id.etBaseUrl)      as EditText
        etTrackA       = findViewById(R.id.etTrackA)       as EditText
        etTrackB       = findViewById(R.id.etTrackB)       as EditText
        cbYoutubeOnly  = findViewById(R.id.cbYoutubeOnly)  as CheckBox
        etBpmA         = findViewById(R.id.etBpmA)         as EditText
        etBpmB         = findViewById(R.id.etBpmB)         as EditText
        cbPitchShift   = findViewById(R.id.cbPitchShift)   as CheckBox
        btnGenerate    = findViewById(R.id.btnGenerate)    as Button
        layoutInput    = findViewById(R.id.layoutInput)    as LinearLayout
        layoutProgress = findViewById(R.id.layoutProgress) as LinearLayout
        progressBar    = findViewById(R.id.progressBar)    as ProgressBar
        tvProgress     = findViewById(R.id.tvProgress)     as TextView
        layoutResult   = findViewById(R.id.layoutResult)   as LinearLayout
        tvResultFile   = findViewById(R.id.tvResultFile)   as TextView
        btnPlay        = findViewById(R.id.btnPlay)        as Button
        btnDownload    = findViewById(R.id.btnDownload)    as Button
        btnReset       = findViewById(R.id.btnReset)       as Button

        btnGenerate.setOnClickListener { startGeneration() }
        btnPlay.setOnClickListener    { togglePlay() }
        btnDownload.setOnClickListener { downloadFile() }
        btnReset.setOnClickListener   { reset() }
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    // ── Generate ──────────────────────────────────────────────────────────────

    private fun startGeneration() {
        val baseUrl     = etBaseUrl.text.toString().trim().ifEmpty { "http://10.0.2.2:8000" }
        val trackA      = etTrackA.text.toString().trim()
        val trackB      = etTrackB.text.toString().trim()
        val youtubeOnly = cbYoutubeOnly.isChecked
        val bpmA        = etBpmA.text.toString().toDoubleOrNull()
        val bpmB        = etBpmB.text.toString().toDoubleOrNull()
        val pitchShift  = cbPitchShift.isChecked

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

        Thread {
            try {
                val job = ApiClient.createMashup(trackA, trackB, youtubeOnly, bpmB, bpmA, pitchShift)
                currentJobId = job.jobId
                pollLoop(job.jobId)
            } catch (e: Exception) {
                mainHandler.post { showError("Connection failed: ${e.message}") }
            }
        }.start()
    }

    private fun pollLoop(jobId: String) {
        while (true) {
            try {
                val job = ApiClient.getJob(jobId)
                mainHandler.post {
                    when (job.status) {
                        "done"   -> showResult()
                        "failed" -> showError(job.message)
                        else     -> updateProgress(job.message, job.progress)
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

    // ── Play ──────────────────────────────────────────────────────────────────

    private fun togglePlay() {
        val jobId = currentJobId ?: return

        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            btnPlay.text = "▶  Play Preview"
            return
        }

        if (mediaPlayer != null) {
            mediaPlayer?.start()
            btnPlay.text = "⏸  Pause"
            return
        }

        // First play — stream from server
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
                mainHandler.post { btnPlay.isEnabled = true; btnPlay.text = "⏸  Pause" }
            }
            setOnCompletionListener {
                mainHandler.post { btnPlay.text = "▶  Play Preview" }
            }
            setOnErrorListener { _, _, _ ->
                mainHandler.post {
                    toast("Playback failed — try Download instead")
                    btnPlay.text = "▶  Play Preview"
                    btnPlay.isEnabled = true
                }
                releasePlayer()
                false
            }
            prepareAsync()
        }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    private fun downloadFile() {
        val jobId    = currentJobId ?: return
        val fileName = currentFileName ?: return

        btnDownload.isEnabled = false
        btnDownload.text = "Downloading…"

        Thread {
            try {
                val url  = URL(ApiClient.downloadUrl(jobId))
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout    = 120_000
                }

                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val dir = File(musicDir, "Mashups").apply { mkdirs() }
                val file = File(dir, fileName)

                conn.inputStream.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }

                MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)

                mainHandler.post {
                    btnDownload.text = "✓  Saved"
                    toast("Saved → ${file.absolutePath}")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    btnDownload.isEnabled = true
                    btnDownload.text = "⬇  Save MP3"
                    toast("Download failed: ${e.message}")
                }
            }
        }.start()
    }

    // ── UI state helpers ──────────────────────────────────────────────────────

    private enum class Section { INPUT, PROGRESS, RESULT }

    private fun setSection(s: Section) {
        layoutInput.visibility    = if (s == Section.INPUT)    View.VISIBLE else View.GONE
        layoutProgress.visibility = if (s == Section.PROGRESS) View.VISIBLE else View.GONE
        layoutResult.visibility   = if (s == Section.RESULT)   View.VISIBLE else View.GONE
    }

    private fun updateProgress(msg: String, pct: Int) {
        tvProgress.text   = msg
        progressBar.progress = pct
    }

    private fun showResult() {
        setSection(Section.RESULT)
        tvResultFile.text     = currentFileName ?: "mashup.mp3"
        btnPlay.isEnabled     = true
        btnPlay.text          = "▶  Play Preview"
        btnDownload.isEnabled = true
        btnDownload.text      = "⬇  Save MP3"
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
        currentJobId          = null
        currentFileName       = null
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun buildFileName(a: String, b: String): String {
        fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9 ]"), "").trim().replace(' ', '_').take(28)
        return "Mashup_${safe(a)}_vs_${safe(b)}.mp3"
    }
}
