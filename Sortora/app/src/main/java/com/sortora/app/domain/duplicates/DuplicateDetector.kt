package com.sortora.app.domain.duplicates

import com.sortora.app.domain.classify.ImageMetrics
import com.sortora.app.domain.model.MediaKind

/** What the detector needs to know about one file. */
data class DuplicateCandidate(
    val itemId: Long,
    val uri: String,
    val kind: MediaKind,
    val sizeBytes: Long,
    /** SHA-256 of the bytes; only computed for files that share a size with another file. */
    val contentHash: String?,
    val perceptualHash: Long?,
    /** L2-normalised image embedding from the on-device model, if available. */
    val embedding: FloatArray?,
    val width: Int?,
    val height: Int?,
    val captureTimeMillis: Long?,
)

data class DuplicateGroup(
    val members: List<Long>,
    /** All members have identical bytes (otherwise they are only visually similar). */
    val exact: Boolean,
    /** The copy to keep: highest resolution, then largest file, then oldest, then first picked. */
    val keeper: Long,
)

/**
 * Finds exact duplicates (same bytes) and near-duplicates (visually similar: re-saved, resized,
 * burst shots), and groups them with union-find.
 *
 * Near-duplicate rule: perceptual hashes at most [STRICT_HASH_DISTANCE] bits apart, or a looser
 * hash match confirmed by the on-device image embedding ([EMBEDDING_SIMILARITY]). Photos are
 * only compared with photos, videos with videos, and only in the same orientation.
 */
object DuplicateDetector {

    const val STRICT_HASH_DISTANCE = 8
    const val LOOSE_HASH_DISTANCE = 18
    const val EMBEDDING_SIMILARITY = 0.92f

    fun findGroups(candidates: List<DuplicateCandidate>): List<DuplicateGroup> {
        val n = candidates.size
        if (n < 2) return emptyList()
        val parent = IntArray(n) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[maxOf(ra, rb)] = minOf(ra, rb)
        }

        // Exact: identical content hashes.
        candidates.withIndex()
            .filter { it.value.contentHash != null }
            .groupBy { it.value.contentHash }
            .values
            .filter { it.size > 1 }
            .forEach { group -> group.drop(1).forEach { union(group.first().index, it.index) } }

        // Near: perceptual hash, confirmed by embeddings when the hash match is loose.
        for (i in 0 until n) {
            val a = candidates[i]
            val ha = a.perceptualHash ?: continue
            for (j in i + 1 until n) {
                val b = candidates[j]
                val hb = b.perceptualHash ?: continue
                if (a.kind != b.kind || !sameOrientation(a, b)) continue
                val distance = ImageMetrics.hammingDistance(ha, hb)
                val similar = when {
                    distance <= STRICT_HASH_DISTANCE -> true
                    distance <= LOOSE_HASH_DISTANCE && a.embedding != null && b.embedding != null ->
                        ImageMetrics.dot(a.embedding, b.embedding) >= EMBEDDING_SIMILARITY
                    else -> false
                }
                if (similar) union(i, j)
            }
        }

        return (0 until n)
            .groupBy(::find)
            .values
            .filter { it.size > 1 }
            .map { indices ->
                val members = indices.map { candidates[it] }
                val hashes = members.map { it.contentHash }
                DuplicateGroup(
                    members = members.map { it.itemId },
                    exact = hashes.all { it != null && it == hashes.first() },
                    keeper = chooseKeeper(members),
                )
            }
            .sortedBy { it.members.min() }
    }

    fun chooseKeeper(members: List<DuplicateCandidate>): Long =
        members.sortedWith(
            compareByDescending<DuplicateCandidate> { (it.width ?: 0).toLong() * (it.height ?: 0) }
                .thenByDescending { it.sizeBytes }
                .thenBy { it.captureTimeMillis ?: Long.MAX_VALUE }
                .thenBy { it.itemId },
        ).first().itemId

    private fun sameOrientation(a: DuplicateCandidate, b: DuplicateCandidate): Boolean {
        val wa = a.width ?: return true
        val ha = a.height ?: return true
        val wb = b.width ?: return true
        val hb = b.height ?: return true
        return (wa >= ha) == (wb >= hb)
    }
}
