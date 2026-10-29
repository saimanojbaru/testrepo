package com.sortora.app.data.local

import com.sortora.app.domain.classify.ImageMetrics
import kotlin.math.roundToInt

/**
 * Stores L2-normalised embeddings as signed bytes (×127). 1 KB per 1024-d vector instead of 4 KB,
 * with negligible effect on cosine similarity.
 */
object EmbeddingCodec {
    fun encode(vector: FloatArray): ByteArray =
        ByteArray(vector.size) { (vector[it] * 127f).roundToInt().coerceIn(-127, 127).toByte() }

    fun decode(bytes: ByteArray): FloatArray =
        ImageMetrics.normalize(FloatArray(bytes.size) { bytes[it] / 127f })
}
