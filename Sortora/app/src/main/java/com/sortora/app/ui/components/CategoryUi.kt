package com.sortora.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.CameraFront
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material.icons.outlined.ShoppingBag
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
    ContentCategory.SCREENSHOT -> Icons.Outlined.Screenshot
    ContentCategory.SELFIE -> Icons.Outlined.CameraFront
    ContentCategory.GROUP -> Icons.Outlined.Groups
    ContentCategory.PEOPLE -> Icons.Outlined.Person
    ContentCategory.DOCUMENT -> Icons.Outlined.Description
    ContentCategory.LANDSCAPE -> Icons.Outlined.Landscape
    ContentCategory.CITY -> Icons.Outlined.Apartment
    ContentCategory.FOOD -> Icons.Outlined.Restaurant
    ContentCategory.PET -> Icons.Outlined.Pets
    ContentCategory.PRODUCT -> Icons.Outlined.ShoppingBag
    ContentCategory.TALKING_HEAD -> Icons.Outlined.RecordVoiceOver
    ContentCategory.B_ROLL -> Icons.Outlined.Movie
    ContentCategory.SCREEN_RECORDING -> Icons.Outlined.ScreenshotMonitor
    ContentCategory.OTHER -> Icons.Outlined.Category
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
    SmartFilter.HAS_FACE -> Icons.Outlined.Face
    SmartFilter.SCREENSHOT -> Icons.Outlined.Screenshot
    SmartFilter.HAS_TEXT -> Icons.Outlined.TextFields
    SmartFilter.LANDSCAPE -> Icons.Outlined.Landscape
    SmartFilter.PRODUCT -> Icons.Outlined.ShoppingBag
    SmartFilter.BLURRY -> Icons.Outlined.BlurOn
    SmartFilter.DUPLICATE -> Icons.Outlined.CopyAll
    SmartFilter.VIDEO -> Icons.Outlined.Movie
}
