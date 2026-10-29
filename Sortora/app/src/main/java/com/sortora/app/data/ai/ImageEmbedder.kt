package com.sortora.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import com.sortora.app.domain.classify.ImageMetrics
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TensorFlow Lite (LiteRT) image embedder: turns an image into a 1024-number vector so that
 * visually similar images have similar vectors.
 *
 * Model: MediaPipe's MobileNet-V3 (small) image embedder, Apache-2.0, bundled in
 * assets/models/image_embedder.tflite (4 MB). Input 224×224 RGB scaled to 0..1 (per the model's
 * metadata). Runs on the CPU with XNNPACK; nothing is downloaded.
 *
 * Used for two things: confirming near-duplicates, and grouping similar-looking faces from aligned
 * face crops. It is a general-purpose model, not a face-recognition model (see README).
 */
class ImageEmbedder private constructor(private val interpreter: Interpreter) : Closeable {

    private val inputHeight: Int
    private val inputWidth: Int
    private val outputSize: Int
    private val input: ByteBuffer
    private val pixels: IntArray

    init {
        val inShape = interpreter.getInputTensor(0).shape() // [1, h, w, 3]
        inputHeight = inShape[1]
        inputWidth = inShape[2]
        outputSize = interpreter.getOutputTensor(0).shape().last()
        input = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * 3).order(ByteOrder.nativeOrder())
        pixels = IntArray(inputWidth * inputHeight)
    }

    /** Returns an L2-normalised embedding. The bitmap is resized (not cropped) to the model input. */
    @Synchronized
    fun embed(bitmap: Bitmap): FloatArray {
        val scaled = if (bitmap.width == inputWidth && bitmap.height == inputHeight) bitmap else bitmap.scale(inputWidth, inputHeight)
        scaled.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        if (scaled !== bitmap) scaled.recycle()
        input.rewind()
        for (p in pixels) {
            input.putFloat(((p shr 16) and 0xFF) / 255f)
            input.putFloat(((p shr 8) and 0xFF) / 255f)
            input.putFloat((p and 0xFF) / 255f)
        }
        input.rewind()
        val output = Array(1) { FloatArray(outputSize) }
        interpreter.run(input, output)
        return ImageMetrics.normalize(output[0])
    }

    override fun close() = interpreter.close()

    companion object {
        const val MODEL_ASSET = "models/image_embedder.tflite"
        const val INPUT_SIZE = 224

        /** Null if the model or the native runtime can't be loaded; callers then skip embeddings. */
        fun create(context: Context): ImageEmbedder? = try {
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
            model.rewind()
            ImageEmbedder(Interpreter(model, Interpreter.Options().setNumThreads(2)))
        } catch (e: Throwable) {
            Log.w("ImageEmbedder", "Embedder unavailable; face grouping and similarity checks are off", e)
            null
        }
    }
}
