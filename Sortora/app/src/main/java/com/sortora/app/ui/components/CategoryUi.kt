package com.sortora.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CopyAll
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.ScreenshotMonitor
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import com.sortora.app.R
import com.sortora.app.domain.filters.SmartFilter
import com.sortora.app.domain.model.ContentCategory

@StringRes
fun ContentCategory.labelRes(): Int = when (this) {
    ContentCategory.SCREENSHOT -> R.string.category_screenshot
    ContentCategory.SELFIE -> R.string.category_selfie
    ContentCategory.GROUP -> R.string.category_group
    ContentCategory.PEOPLE -> R.string.category_people
    ContentCategory.DOCUMENT -> R.string.category_document
    ContentCategory.LANDSCAPE -> R.string.category_landscape
    ContentCategory.CITY -> R.string.category_city
    ContentCategory.FOOD -> R.string.category_food
    ContentCategory.PET -> R.string.category_pet
    ContentCategory.PRODUCT -> R.string.category_product
    ContentCategory.TALKING_HEAD -> R.string.category_talking_head
    ContentCategory.B_ROLL -> R.string.category_b_roll
    ContentCategory.SCREEN_RECORDING -> R.string.category_screen_recording
    ContentCategory.OTHER -> R.string.category_other
}

fun ContentCategory.icon(): ImageVector = when (this) {
    ContentCategory.SCREENSHOT -> Icons.Rounded.Screenshot
    ContentCategory.SELFIE -> Icons.Rounded.CameraFront
    ContentCategory.GROUP -> Icons.Rounded.Groups
    ContentCategory.PEOPLE -> Icons.Rounded.Person
    ContentCategory.DOCUMENT -> Icons.Rounded.Description
    ContentCategory.LANDSCAPE -> Icons.Rounded.Landscape
    ContentCategory.CITY -> Icons.Rounded.Apartment
    ContentCategory.FOOD -> Icons.Rounded.Restaurant
    ContentCategory.PET -> Icons.Rounded.Pets
    ContentCategory.PRODUCT -> Icons.Rounded.ShoppingBag
    ContentCategory.TALKING_HEAD -> Icons.Rounded.RecordVoiceOver
    ContentCategory.B_ROLL -> Icons.Rounded.Movie
    ContentCategory.SCREEN_RECORDING -> Icons.Rounded.ScreenshotMonitor
    ContentCategory.OTHER -> Icons.Rounded.Category
}

@StringRes
fun SmartFilter.labelRes(): Int = when (this) {
    SmartFilter.HAS_FACE -> R.string.filter_has_face
    SmartFilter.SCREENSHOT -> R.string.filter_screenshot
    SmartFilter.HAS_TEXT -> R.string.filter_has_text
    SmartFilter.LANDSCAPE -> R.string.filter_landscape
    SmartFilter.PRODUCT -> R.string.filter_product
    SmartFilter.BLURRY -> R.string.filter_blurry
    SmartFilter.DUPLICATE -> R.string.filter_duplicate
    SmartFilter.VIDEO -> R.string.filter_video
}

fun SmartFilter.icon(): ImageVector = when (this) {
    SmartFilter.HAS_FACE -> Icons.Rounded.Face
    SmartFilter.SCREENSHOT -> Icons.Rounded.Screenshot
    SmartFilter.HAS_TEXT -> Icons.Rounded.TextFields
    SmartFilter.LANDSCAPE -> Icons.Rounded.Landscape
    SmartFilter.PRODUCT -> Icons.Rounded.ShoppingBag
    SmartFilter.BLURRY -> Icons.Rounded.BlurOn
    SmartFilter.DUPLICATE -> Icons.Rounded.CopyAll
    SmartFilter.VIDEO -> Icons.Rounded.Movie
}
