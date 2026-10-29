package com.sortora.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.roundToInt

/** Samples evenly spaced key frames from a video, plus its container metadata. */
class VideoFrameExtractor @Inject constructor(@ApplicationContext private val context: Context) {

    data class Frame(val bitmap: Bitmap, val rotationDegrees: Int)

    data class VideoInfo(
        val durationMs: Long?,
        /** Display-oriented size (rotation already applied). */
        val width: Int?,
        val height: Int?,
        val hasAudio: Boolean?,
        val containerDate: String?,
    )

    data class Result(val frames: List<Frame>, val info: VideoInfo)

    fun extract(uri: Uri, frameCount: Int, maxDimension: Int): Result {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val info = readInfo(retriever)
            val duration = info.durationMs ?: 0L
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()

            val count = frameCount.coerceIn(1, 12)
            val frames = (0 until count).mapNotNull { index ->
                // Sample the middle of each slice, skipping the very first/last moments (fades, black frames).
                val timeUs = if (duration > 0) (duration * 1000L * (2 * index + 1)) / (2L * count) else 0L
                grabFrame(retriever, timeUs, maxDimension)?.let { bitmap ->
                    Frame(bitmap, residualRotation(bitmap, rawWidth, rawHeight, rotation))
                }
            }
            return Result(frames, info)
        } catch (_: RuntimeException) {
            // Corrupt or unsupported file: report what we have (nothing).
            return Result(emptyList(), VideoInfo(null, null, null, null, null))
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readInfo(retriever: MediaMetadataRetriever): VideoInfo {
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
        val swap = rotation == 90 || rotation == 270
        return VideoInfo(
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
            width = if (swap) height else width,
            height = if (swap) width else height,
            hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)?.let { it == "yes" },
            containerDate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE),
        )
    }

    private fun grabFrame(retriever: MediaMetadataRetriever, timeUs: Long, maxDimension: Int): Bitmap? {
        val option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(timeUs, option, maxDimension, maxDimension)
        } else {
            retriever.getFrameAtTime(timeUs, option)?.let { scaleDown(it, maxDimension) }
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest
        return bitmap.scale((bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt())
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    /**
     * Most devices return frames already rotated, some don't. If the frame's orientation doesn't
     * match the display orientation, tell ML Kit to rotate it.
     */
    private fun residualRotation(frame: Bitmap, rawWidth: Int?, rawHeight: Int?, rotation: Int): Int {
        if (rotation != 90 && rotation != 270) return 0
        if (rawWidth == null || rawHeight == null || rawWidth == rawHeight) return 0
        val frameIsLandscape = frame.width > frame.height
        val rawIsLandscape = rawWidth > rawHeight
        return if (frameIsLandscape == rawIsLandscape) rotation else 0
    }
}
