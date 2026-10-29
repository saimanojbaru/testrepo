package com.sortora.app.domain

import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DateStyle
import com.sortora.app.domain.model.NamingPreset
import com.sortora.app.domain.model.NamingSettings
import com.sortora.app.domain.model.Separator
import com.sortora.app.domain.naming.FileNameGenerator
import com.sortora.app.domain.naming.NameInput
import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameGeneratorTest {

    private val generator = FileNameGenerator(UTC)
    private val date = millis(2025, 10, 12, 14, 30, 15)

    private fun input(
        category: ContentCategory = ContentCategory.TALKING_HEAD,
        tags: List<String> = listOf("person", "desk"),
        ext: String = "mp4",
        title: String? = null,
        captured: Long? = date,
        original: String = "VID_20251012_143015",
    ) = NameInput(original, ext, captured, category, tags, title)

    @Test
    fun `default template matches the spec example`() {
        assertEquals("2025-10-12_talkinghead_person_desk.mp4", generator.generate(input(), NamingSettings()))
    }

    @Test
    fun `screenshots use the on-screen title`() {
        val name = generator.generate(input(ContentCategory.SCREENSHOT, emptyList(), "png", title = "Settings"), NamingSettings())
        assertEquals("Screenshot_Settings.png", name)
    }

    @Test
    fun `screenshots without a title fall back to date and time`() {
        val name = generator.generate(input(ContentCategory.SCREENSHOT, emptyList(), "png"), NamingSettings())
        assertEquals("Screenshot_2025-10-12_143015.png", name)
    }

    @Test
    fun `multi word titles become PascalCase`() {
        val name = generator.generate(input(ContentCategory.SCREENSHOT, emptyList(), "png", title = "Network & internet"), NamingSettings())
        assertEquals("Screenshot_NetworkInternet.png", name)
    }

    @Test
    fun `empty tokens do not leave double separators`() {
        val name = generator.generate(input(ContentCategory.OTHER, listOf("dog"), "jpg"), NamingSettings())
        assertEquals("2025-10-12_dog.jpg", name)
    }

    @Test
    fun `hyphen separator applies to preset templates and multi-word tags`() {
        val settings = NamingSettings(separator = Separator.HYPHEN)
        val name = generator.generate(input(tags = listOf("mobile phone", "desk")), settings)
        assertEquals("2025-10-12-talkinghead-mobile_phone-desk.mp4", name)
    }

    @Test
    fun `max labels and compact dates are respected`() {
        val settings = NamingSettings(dateStyle = DateStyle.COMPACT, maxLabels = 1)
        assertEquals("20251012_talkinghead_person.mp4", generator.generate(input(), settings))
    }

    @Test
    fun `custom template with time and original name`() {
        val settings = NamingSettings(preset = NamingPreset.CUSTOM, customTemplate = "{date}_{time}_{original}")
        assertEquals("2025-10-12_143015_vid_20251012_143015.mp4", generator.generate(input(), settings))
    }

    @Test
    fun `no date and no tags falls back to the original name`() {
        val name = generator.generate(input(ContentCategory.OTHER, emptyList(), "jpg", captured = null, original = "IMG 0001"), NamingSettings())
        assertEquals("IMG_0001.jpg", name)
    }

    @Test
    fun `user typed names are sanitised and keep the extension`() {
        val name = generator.sanitizeUserName("Trip: day 1/2?.MP4", "mp4", NamingSettings())
        assertEquals("Trip_day_12.mp4", name)
    }

    @Test
    fun `lowercase can be turned off`() {
        val settings = NamingSettings(lowercase = false)
        assertEquals("2025-10-12_talkinghead_Person_Desk.mp4", generator.generate(input(tags = listOf("Person", "Desk")), settings))
    }
}
