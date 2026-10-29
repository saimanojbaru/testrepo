package com.sortora.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Built-in content categories produced by the on-device classifier.
 *
 * @property defaultFolder folder name used unless the user renames it in Settings.
 * @property nameToken compact token used in generated file names (e.g. "talkinghead").
 * @property appliesTo which media kinds can end up in this category.
 */
@Serializable
enum class ContentCategory(
    val defaultFolder: String,
    val nameToken: String,
    val appliesTo: Set<MediaKind>,
) {
    SCREENSHOT("Screenshots", "screenshot", setOf(MediaKind.IMAGE)),
    SELFIE("Selfies", "selfie", setOf(MediaKind.IMAGE)),
    GROUP("Group Photos", "group", setOf(MediaKind.IMAGE)),
    PEOPLE("People", "people", setOf(MediaKind.IMAGE)),
    DOCUMENT("Documents", "document", setOf(MediaKind.IMAGE)),
    LANDSCAPE("Landscapes", "landscape", setOf(MediaKind.IMAGE)),
    CITY("City & Architecture", "city", setOf(MediaKind.IMAGE)),
    FOOD("Food", "food", setOf(MediaKind.IMAGE)),
    PET("Pets & Animals", "animal", setOf(MediaKind.IMAGE)),
    PRODUCT("Products", "product", setOf(MediaKind.IMAGE)),
    TALKING_HEAD("Talking Head", "talkinghead", setOf(MediaKind.VIDEO)),
    B_ROLL("B-Roll", "broll", setOf(MediaKind.VIDEO)),
    SCREEN_RECORDING("Screen Recordings", "screenrec", setOf(MediaKind.VIDEO)),
    OTHER("Other", "", setOf(MediaKind.IMAGE, MediaKind.VIDEO)),
}
