package com.sortora.app.data.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Cuts faces out of a photo in a consistent way, so their embeddings are comparable: the eyes are
 * rotated level, scaled to a fixed distance and placed at the same spot in a square crop.
 */
object FaceCrops {

    /** Smallest face (in pixels of the analysed image) worth embedding. */
    const val MIN_FACE_PX = 40
    private const val THUMBNAIL_PX = 96

    fun aligned(source: Bitmap, box: Rect, eyes: Pair<PointF, PointF>?, size: Int = ImageEmbedder.INPUT_SIZE): Bitmap {
        val matrix = Matrix()
        if (eyes != null) {
            // Order the eyes by x so the result doesn't depend on which one the detector called "left".
            val (a, b) = if (eyes.first.x <= eyes.second.x) eyes else eyes.second to eyes.first
            val dx = b.x - a.x
            val dy = b.y - a.y
            val distance = hypot(dx, dy).coerceAtLeast(1f)
            val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            val scale = size * 0.36f / distance
            matrix.postTranslate(-(a.x + b.x) / 2f, -(a.y + b.y) / 2f)
            matrix.postRotate(-angle)
            matrix.postScale(scale, scale)
            matrix.postTranslate(size / 2f, size * 0.4f)
        } else {
            val side = max(box.width(), box.height()) * 1.25f
            val scale = size / side.coerceAtLeast(1f)
            matrix.postTranslate(-box.exactCenterX(), -box.exactCenterY())
            matrix.postScale(scale, scale)
            matrix.postTranslate(size / 2f, size / 2f)
        }
        val out = createBitmap(size, size)
        Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    fun thumbnailJpeg(crop: Bitmap): ByteArray {
        val small = crop.scale(THUMBNAIL_PX, THUMBNAIL_PX)
        return ByteArrayOutputStream().use { stream ->
            small.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            if (small !== crop) small.recycle()
            stream.toByteArray()
        }
    }
}
