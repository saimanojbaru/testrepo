package com.hitit.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.hitit.domain.pose.PoseLandmark

/**
 * CameraX → MediaPipe bridge: feeds live frames to the PoseLandmarker (LIVE_STREAM) and emits the
 * first detected body as domain [PoseLandmark]s (null when no body is in frame). Follows the
 * verified contract: monotonic uptime timestamps, every ImageProxy closed, frame rotated by
 * rotationDegrees and mirrored for the front camera BEFORE detection, results on a worker thread
 * (Compose snapshot state is safe to write from there).
 */
class PoseAnalyzer(
    context: Context,
    private val isFrontCamera: Boolean = true,
    private val onPose: (List<PoseLandmark>?) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {

    @Volatile
    private var closed = false

    private val landmarker: PoseLandmarker = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .build(),
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setResultListener { result: PoseLandmarkerResult, _: MPImage ->
                val pose = result.landmarks().firstOrNull()?.map { lm ->
                    PoseLandmark(lm.x(), lm.y(), lm.z(), lm.visibility().orElse(1f))
                }
                if (!closed) onPose(pose)
            }
            .setErrorListener { if (!closed) onPose(null) }
            .build(),
    )

    override fun analyze(imageProxy: ImageProxy) {
        if (closed) {
            imageProxy.close()
            return
        }
        val timestampMs = SystemClock.uptimeMillis()
        imageProxy.use { proxy ->
            val bitmap = proxy.toBitmap()
            // toBitmap() does NOT rotate; landmark x/y are normalized to the bitmap we feed, so
            // rotate to upright and mirror selfie frames here.
            val matrix = Matrix().apply {
                postRotate(proxy.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f, proxy.width.toFloat(), proxy.height.toFloat())
                }
            }
            val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            runCatching { landmarker.detectAsync(BitmapImageBuilder(upright).build(), timestampMs) }
        }
    }

    override fun close() {
        closed = true
        runCatching { landmarker.close() }
    }

    private companion object {
        const val MODEL_ASSET = "pose_landmarker_lite.task"
    }
}
