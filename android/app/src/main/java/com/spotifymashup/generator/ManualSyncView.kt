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

/**
 * Visualises multiple tracks as colour-coded horizontal bars on a shared timeline.
 * Each bar has a drag handle on the left so the user can set the start offset
 * (how many beats to trim from the beginning of that track).
 *
 * Expose [onOffsetChanged] to receive callbacks when a handle is dragged.
 */
class ManualSyncView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    // ── Public API ────────────────────────────────────────────────────────────

    data class TrackBar(
        var label: String,
        var color: Int,
        /** 0.0-1.0 — fraction of the total timeline width trimmed from the start */
        var offsetFraction: Float = 0f,
        var bpm: Float = 120f,
    )

    var tracks: List<TrackBar> = emptyList()
        set(value) { field = value; invalidate() }

    var onOffsetChanged: ((trackIndex: Int, newOffset: Float) -> Unit)? = null

    // ── Internal paint / geometry ─────────────────────────────────────────────

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt(); textSize = 28f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22FFFFFF.toInt(); strokeWidth = 1f
    }
    private val bgPaint = Paint().apply { color = 0xFF181E28.toInt() }
    private val beatCountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44FFFFFF.toInt(); textSize = 22f
    }

    private val BAR_PADDING = 6f
    private val HANDLE_WIDTH = 18f
    private val CORNER = 8f

    private var draggingIdx: Int = -1
    private var dragStartX: Float = 0f
    private var dragStartOffset: Float = 0f

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (tracks.isEmpty()) return

        // Background
        canvas.drawRoundRect(RectF(0f, 0f, w, h), CORNER, CORNER, bgPaint)

        // Beat grid (8 divisions)
        val divisions = 8
        for (i in 1 until divisions) {
            val x = w * i / divisions
            canvas.drawLine(x, 4f, x, h - 4f, gridPaint)
            canvas.drawText("${i * 4}", x + 3, h - 6f, beatCountPaint)
        }

        val barHeight = ((h - BAR_PADDING * (tracks.size + 1)) / tracks.size)
            .coerceAtLeast(12f)
        val usableW = w - BAR_PADDING * 2

        tracks.forEachIndexed { i, bar ->
            val top = BAR_PADDING + i * (barHeight + BAR_PADDING)
            val bottom = top + barHeight
            val startX = BAR_PADDING + bar.offsetFraction * usableW

            // Bar gradient
            val grad = LinearGradient(
                startX, 0f, w - BAR_PADDING, 0f,
                intArrayOf(bar.color, (bar.color and 0x00FFFFFF) or 0x55000000),
                null, Shader.TileMode.CLAMP,
            )
            barPaint.shader = grad
            val rect = RectF(startX, top, w - BAR_PADDING, bottom)
            canvas.drawRoundRect(rect, CORNER / 2, CORNER / 2, barPaint)

            // Handle
            val handleRect = RectF(startX, top, startX + HANDLE_WIDTH, bottom)
            handlePaint.alpha = if (draggingIdx == i) 220 else 140
            canvas.drawRoundRect(handleRect, CORNER / 2, CORNER / 2, handlePaint)

            // Label
            labelPaint.textSize = (barHeight * 0.45f).coerceIn(20f, 34f)
            canvas.drawText(bar.label, startX + HANDLE_WIDTH + 8f, top + barHeight * 0.68f, labelPaint)
        }
    }

    // ── Touch / drag ──────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (tracks.isEmpty() || w == 0f) return false

        val barHeight = ((h - BAR_PADDING * (tracks.size + 1)) / tracks.size)
            .coerceAtLeast(12f)

        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggingIdx = -1
                tracks.forEachIndexed { i, bar ->
                    val top = BAR_PADDING + i * (barHeight + BAR_PADDING)
                    val bottom = top + barHeight
                    val handleX = BAR_PADDING + bar.offsetFraction * (w - BAR_PADDING * 2)
                    if (event.x in handleX..(handleX + HANDLE_WIDTH + 20f) &&
                        event.y in top..bottom) {
                        draggingIdx = i
                        dragStartX = event.x
                        dragStartOffset = bar.offsetFraction
                    }
                }
                draggingIdx >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingIdx < 0) return false
                val usableW = w - BAR_PADDING * 2
                val delta = (event.x - dragStartX) / usableW
                val newOffset = (dragStartOffset + delta).coerceIn(0f, 0.8f)
                tracks[draggingIdx].offsetFraction = newOffset
                onOffsetChanged?.invoke(draggingIdx, newOffset)
                invalidate()
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingIdx = -1
                invalidate()
                true
            }
            else -> false
        }
    }
}
