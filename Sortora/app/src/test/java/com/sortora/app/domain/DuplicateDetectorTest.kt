package com.sortora.app.domain

import com.sortora.app.domain.classify.ImageMetrics
import com.sortora.app.domain.duplicates.DuplicateCandidate
import com.sortora.app.domain.duplicates.DuplicateDetector
import com.sortora.app.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {

    private fun candidate(
        id: Long,
        hash: Long?,
        content: String? = null,
        size: Long = 1000,
        w: Int = 4000,
        h: Int = 3000,
        kind: MediaKind = MediaKind.IMAGE,
        embedding: FloatArray? = null,
    ) = DuplicateCandidate(id, "content://$id", kind, size, content, hash, embedding, w, h, null)

    @Test
    fun `identical bytes form an exact group`() {
        val groups = DuplicateDetector.findGroups(
            listOf(candidate(1, 0L, "abc"), candidate(2, 0L, "abc"), candidate(3, -1L, "zzz")),
        )
        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L), groups[0].members)
        assertTrue(groups[0].exact)
    }

    @Test
    fun `close perceptual hashes are near duplicates and the biggest copy is kept`() {
        val base = 0x0F0F_0F0F_0F0F_0F0FL
        val groups = DuplicateDetector.findGroups(
            listOf(candidate(1, base, w = 1000, h = 750), candidate(2, base xor 0b111, w = 4000, h = 3000), candidate(3, base.inv())),
        )
        assertEquals(1, groups.size)
        assertFalse(groups[0].exact)
        assertEquals(2L, groups[0].keeper)
    }

    @Test
    fun `loose hash matches need the embedding to agree`() {
        val base = 0L
        val loose = (1L shl 12) - 1 // 12 bits apart
        val e1 = ImageMetrics.normalize(floatArrayOf(1f, 0.1f, 0f))
        val e2 = ImageMetrics.normalize(floatArrayOf(1f, 0.12f, 0f))
        val e3 = ImageMetrics.normalize(floatArrayOf(0f, 1f, 0f))
        assertEquals(1, DuplicateDetector.findGroups(listOf(candidate(1, base, embedding = e1), candidate(2, loose, embedding = e2))).size)
        assertTrue(DuplicateDetector.findGroups(listOf(candidate(1, base, embedding = e1), candidate(2, loose, embedding = e3))).isEmpty())
        assertTrue(DuplicateDetector.findGroups(listOf(candidate(1, base), candidate(2, loose))).isEmpty())
    }

    @Test
    fun `photos are not matched with videos or across orientations`() {
        assertTrue(DuplicateDetector.findGroups(listOf(candidate(1, 0L), candidate(2, 0L, kind = MediaKind.VIDEO))).isEmpty())
        assertTrue(DuplicateDetector.findGroups(listOf(candidate(1, 0L, w = 4000, h = 3000), candidate(2, 0L, w = 3000, h = 4000))).isEmpty())
    }
}
