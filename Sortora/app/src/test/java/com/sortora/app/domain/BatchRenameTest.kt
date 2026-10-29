package com.sortora.app.domain

import com.sortora.app.domain.filters.SmartFilter
import com.sortora.app.domain.model.AppSettings
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DuplicateInfo
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.domain.model.NamingSettings
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.naming.FileNameGenerator
import com.sortora.app.domain.naming.NameInput
import com.sortora.app.domain.planning.PlanOptions
import com.sortora.app.domain.planning.SortPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRenameTest {

    private val generator = FileNameGenerator(UTC)
    private val planner = SortPlanner()
    private val date = millis(2025, 10, 12, 14, 30, 15)

    @Test
    fun `new tokens render`() {
        val input = NameInput(
            "IMG_1", "jpg", date, ContentCategory.PET, listOf("golden retriever", "beach"), null,
            counter = 7, folder = "Pets/2025", people = listOf("Mom"), camera = "Pixel 9 Pro",
        )
        val name = generator.generate(input, NamingSettings(), "{year}{month}{day}_{label}_{counter}_{counter:2}_{folder}_{people}_{camera}")
        assertEquals("20251012_golden-retriever_007_07_2025_mom_pixel-9-pro.jpg", name)
    }

    @Test
    fun `counter numbers files per folder in capture order`() {
        val items = listOf(
            item(1, "c.jpg", ContentCategory.PET, listOf("dog"), date = millis(2025, 1, 3)),
            item(2, "a.jpg", ContentCategory.PET, listOf("dog"), date = millis(2025, 1, 1)),
            item(3, "b.jpg", ContentCategory.FOOD, listOf("pizza"), date = millis(2025, 1, 2)),
            item(4, "d.jpg", ContentCategory.PET, listOf("dog"), date = millis(2025, 1, 2)),
        ).map { it.copy(customPattern = "Trip_{counter:2}") }
        val plan = planner.plan(items, AppSettings(), zone = UTC)
        assertEquals("Trip_03.jpg", plan.getValue(1).name)
        assertEquals("Trip_01.jpg", plan.getValue(2).name)
        assertEquals("Trip_01.jpg", plan.getValue(3).name) // different folder → own sequence
        assertEquals("Trip_02.jpg", plan.getValue(4).name)
    }

    @Test
    fun `rename mode keeps files in place and dedupes per source folder`() {
        val items = listOf(
            item(1, "a.jpg", ContentCategory.PET, listOf("dog")).let { it.copy(file = it.file.copy(pathHint = "DCIM/Camera", parentUri = "p1")) },
            item(2, "b.jpg", ContentCategory.PET, listOf("dog")).let { it.copy(file = it.file.copy(pathHint = "DCIM/Camera", parentUri = "p1")) },
            item(3, "c.jpg", ContentCategory.PET, listOf("dog")).let { it.copy(file = it.file.copy(pathHint = "Download", parentUri = "p2")) },
        )
        val plan = planner.plan(items, AppSettings(), PlanOptions(OperationMode.RENAME), UTC)
        assertTrue(plan.values.all { it.inPlace })
        assertEquals("DCIM/Camera", plan.getValue(1).folder)
        assertEquals("2025-10-12_animal_dog.jpg", plan.getValue(1).name)
        assertEquals("2025-10-12_animal_dog_2.jpg", plan.getValue(2).name)
        assertEquals("2025-10-12_animal_dog.jpg", plan.getValue(3).name)
    }

    @Test
    fun `named people lead the keywords`() {
        val plan = planner.plan(
            listOf(item(1, "a.jpg", ContentCategory.PEOPLE, listOf("smile"))),
            AppSettings(),
            PlanOptions(people = mapOf(1L to listOf("Mom"))),
            UTC,
        )
        assertEquals("2025-10-12_people_mom_smile.jpg", plan.getValue(1).name)
    }

    @Test
    fun `smart filters`() {
        val blurry = item(1, "a.jpg", ContentCategory.LANDSCAPE).let { it.copy(analysis = it.analysis!!.copy(sharpness = 5f)) }
        val sharp = item(2, "b.jpg", ContentCategory.PRODUCT).let { it.copy(analysis = it.analysis!!.copy(sharpness = 500f)) }
        val dup = item(3, "c.mp4", ContentCategory.B_ROLL, kind = MediaKind.VIDEO).copy(duplicate = DuplicateInfo(1, true, false))
        val all = listOf(blurry, sharp, dup)
        assertEquals(listOf(blurry), SmartFilter.apply(all, setOf(SmartFilter.BLURRY)))
        assertEquals(listOf(blurry), SmartFilter.apply(all, setOf(SmartFilter.LANDSCAPE, SmartFilter.BLURRY)))
        assertEquals(listOf(sharp), SmartFilter.apply(all, setOf(SmartFilter.PRODUCT)))
        assertEquals(listOf(dup), SmartFilter.apply(all, setOf(SmartFilter.DUPLICATE, SmartFilter.VIDEO)))
        assertEquals(all, SmartFilter.apply(all, emptySet()))
    }
}
