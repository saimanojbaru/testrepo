package com.spotifymashup.generator

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated waveform with optional hook-region highlights and a draggable
 * playhead. Falls back to a procedurally generated wave when no real
 * sample data is provided.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF7C5CFF.toInt()
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x551DE782.toInt()
    }
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 4f
        color = 0xFFFFFFFF.toInt()
    }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x33FFFFFF
    }

    /** 0-1 amplitudes; if null, a procedural wave is used. */
    private var samples: FloatArray = procedural(220)

    /** Optional hook region (0-1 fractions) to highlight. */
    private var hookStart: Float = -1f
    private var hookEnd: Float = -1f

    /** Playhead in 0-1 of total. */
    var playheadFraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var onSeek: ((Float) -> Unit)? = null

    fun setSamples(values: FloatArray) {
        samples = values.takeIf { it.isNotEmpty() } ?: procedural(220)
        invalidate()
    }

    fun setHookRegion(startFrac: Float, endFrac: Float) {
        hookStart = startFrac.coerceIn(0f, 1f)
        hookEnd = endFrac.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Hook region highlight (drawn behind bars)
        if (hookEnd > hookStart) {
            val left = w * hookStart
            val right = w * hookEnd
            canvas.drawRoundRect(RectF(left, 4f, right, h - 4f), 12f, 12f, highlightPaint)
        }

        // Baseline
        canvas.drawLine(0f, h / 2f, w, h / 2f, baselinePaint)

        // Bars
        val n = samples.size
        val barSpace = w / n
        val barWidth = max(barSpace * 0.55f, 2f)
        val maxHalf = h / 2f - 6f

        // Lazy gradient — only rebuild when size changes.
        if (barPaint.shader == null || _shaderW != w) {
            barPaint.shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(0xFF1DE782.toInt(), 0xFF7C5CFF.toInt(), 0xFFFF4D9D.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            _shaderW = w
        }

        for (i in 0 until n) {
            val a = samples[i]
            val cx = i * barSpace + barSpace / 2f
            val half = a * maxHalf
            canvas.drawRoundRect(
                RectF(cx - barWidth / 2f, h / 2f - half, cx + barWidth / 2f, h / 2f + half),
                barWidth, barWidth, barPaint,
            )
        }

        // Playhead
        val px = w * playheadFraction
        canvas.drawCircle(px, h / 2f, 8f, playheadPaint)
        canvas.drawLine(px, 4f, px, h - 4f, playheadPaint)
    }

    private var _shaderW = -1f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_MOVE) {
            val frac = (event.x / max(width, 1)).coerceIn(0f, 1f)
            playheadFraction = frac
            onSeek?.invoke(frac)
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun procedural(n: Int): FloatArray {
        val rng = Random(42)
        return FloatArray(n) { i ->
            val base = sin(i * 2.0 * PI / n).toFloat()
            val lump = sin(i * 6.0 * PI / n).toFloat() * 0.4f
            val noise = rng.nextFloat() * 0.18f
            min(1f, max(0.05f, abs(base + lump) * 0.85f + noise))
        }
    }
}

/**
 * Compact waveform thumbnail used inside hook cards. No interaction.
 */
class MiniWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var samples: FloatArray = procedural(80, seed = 0)

    fun setSeed(seed: Int) {
        samples = procedural(80, seed)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (paint.shader == null || _shaderW != w) {
            paint.shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(0xFF1DE782.toInt(), 0xFF7C5CFF.toInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            _shaderW = w
        }
        val n = samples.size
        val space = w / n
        val barW = max(space * 0.55f, 2f)
        val maxHalf = h / 2f - 2f
        for (i in 0 until n) {
            val a = samples[i]
            val cx = i * space + space / 2f
            val half = a * maxHalf
            canvas.drawRoundRect(
                RectF(cx - barW / 2f, h / 2f - half, cx + barW / 2f, h / 2f + half),
                barW, barW, paint,
            )
        }
    }

    private var _shaderW = -1f

    private fun procedural(n: Int, seed: Int): FloatArray {
        val rng = Random(seed * 31L + 7L)
        return FloatArray(n) { i ->
            val base = sin(i * 2.0 * PI / n).toFloat()
            min(1f, max(0.1f, abs(base) * 0.75f + rng.nextFloat() * 0.25f))
        }
    }
}
