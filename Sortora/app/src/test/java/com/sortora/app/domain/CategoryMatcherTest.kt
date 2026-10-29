package com.sortora.app.domain

import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.UserCategory
import com.sortora.app.domain.planning.CategoryMatcher
import com.sortora.app.domain.planning.MatchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryMatcherTest {

    private val pets = UserCategory("1", "Pets")
    private val receipts = UserCategory("2", "Receipts")
    private val client = UserCategory("3", "Client Acme", keywords = listOf("acme", "laptop"))
    private val aroll = UserCategory("4", "A-Roll")
    private val photos = UserCategory("5", "Travel Photos 2025")

    private fun match(
        category: ContentCategory = ContentCategory.OTHER,
        labels: List<com.sortora.app.domain.model.DetectedLabel> = emptyList(),
        text: List<String> = emptyList(),
        name: String = "IMG_0001",
        folders: List<UserCategory> = listOf(pets, receipts, client, aroll, photos),
    ) = CategoryMatcher.bestMatch(MatchInput(category, labels, text, name), folders)?.category

    @Test
    fun `folder name concepts catch related labels`() {
        assertEquals(pets, match(ContentCategory.PET, listOf(label("Dog", 0.9f))))
        assertEquals(pets, match(labels = listOf(label("Cat", 0.8f))))
    }

    @Test
    fun `receipt words in OCR text`() {
        assertEquals(receipts, match(ContentCategory.DOCUMENT, text = listOf("subtotal", "tax", "total", "visa")))
    }

    @Test
    fun `a single weak text hit is not enough`() {
        assertNull(match(text = listOf("total")))
    }

    @Test
    fun `explicit keywords match labels and file names`() {
        assertEquals(client, match(labels = listOf(label("Laptop", 0.8f))))
        assertEquals(client, match(name = "acme_launch_final"))
    }

    @Test
    fun `category concepts like a-roll map to talking heads`() {
        assertEquals(aroll, match(ContentCategory.TALKING_HEAD))
    }

    @Test
    fun `generic folder words and years are ignored`() {
        // "Travel Photos 2025" should not match an OCR "photos" or "2025"; only travel concepts.
        assertNull(match(text = listOf("photos", "2025")))
        assertEquals(photos, match(ContentCategory.LANDSCAPE, listOf(label("Beach", 0.9f)), folders = listOf(photos)))
    }

    @Test
    fun `unrelated content falls through`() {
        assertNull(match(ContentCategory.FOOD, listOf(label("Pizza", 0.9f))))
    }
}
