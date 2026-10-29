package com.sortora.app.domain

import com.sortora.app.domain.model.AppSettings
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.domain.model.UserCategory
import com.sortora.app.domain.planning.SortPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SortPlannerTest {

    private val planner = SortPlanner()

    @Test
    fun `items go to built-in folders with generated names`() {
        val plan = planner.plan(
            listOf(item(1, "VID_1.mp4", ContentCategory.TALKING_HEAD, listOf("person", "desk"), kind = MediaKind.VIDEO)),
            AppSettings(),
            UTC,
        )
        assertEquals("Talking Head", plan.getValue(1).folder)
        assertEquals("2025-10-12_talkinghead_person_desk.mp4", plan.getValue(1).name)
    }

    @Test
    fun `duplicate names in one folder get numeric suffixes`() {
        val items = (1L..3L).map { item(it, "IMG_$it.jpg", ContentCategory.PET, listOf("dog")) }
        val names = planner.plan(items, AppSettings(), UTC).values.map { it.name }
        assertEquals(listOf("2025-10-12_animal_dog.jpg", "2025-10-12_animal_dog_2.jpg", "2025-10-12_animal_dog_3.jpg"), names)
    }

    @Test
    fun `excluded items do not reserve names`() {
        val items = listOf(
            item(1, "a.jpg", ContentCategory.PET, listOf("dog"), included = false),
            item(2, "b.jpg", ContentCategory.PET, listOf("dog")),
        )
        assertEquals("2025-10-12_animal_dog.jpg", planner.plan(items, AppSettings(), UTC).getValue(2).name)
    }

    @Test
    fun `user categories, renamed folders and year grouping`() {
        val settings = AppSettings(
            customCategories = listOf(UserCategory("x", "Dogs")),
            folderNames = mapOf(ContentCategory.FOOD to "Yum"),
            groupByYear = true,
        )
        val plan = planner.plan(
            listOf(
                item(1, "a.jpg", ContentCategory.PET, labels = listOf(label("Dog", 0.9f))),
                item(2, "b.jpg", ContentCategory.FOOD),
            ),
            settings,
            UTC,
        )
        assertEquals("Dogs/2025", plan.getValue(1).folder)
        assertNotNull(plan.getValue(1).userCategoryMatch)
        assertEquals("Yum/2025", plan.getValue(2).folder)
    }

    @Test
    fun `user edits override suggestions and are sanitised`() {
        val plan = planner.plan(
            listOf(item(1, "a.jpg", ContentCategory.PET, customName = "Best dog ever!", customFolder = "Clients/ Acme ")),
            AppSettings(),
            UTC,
        )
        assertEquals("Best_dog_ever!.jpg", plan.getValue(1).name)
        assertEquals("Clients/Acme", plan.getValue(1).folder)
    }

    @Test
    fun `reserveName is case insensitive`() {
        val taken = mutableSetOf("photo.jpg")
        assertEquals("Photo_2.JPG", SortPlanner.reserveName("Photo.JPG", '_', taken))
    }
}
