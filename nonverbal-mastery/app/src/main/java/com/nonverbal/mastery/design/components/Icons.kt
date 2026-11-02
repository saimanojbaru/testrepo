package com.nonverbal.mastery.design.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A small, hand-drawn icon set on a 24-unit grid with a 1.6 stroke and round ends —
 * lighter than Material's defaults, to sit beside serif text without shouting.
 */
object NvIcons {
    private fun icon(name: String, stroke: Float = 1.6f, fill: Boolean = false, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = if (fill) SolidColor(Color.Black) else null,
                stroke = if (fill) null else SolidColor(Color.Black),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    val Back = icon("back") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
    val Close = icon("close") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    val Forward = icon("forward") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
    val Down = icon("down") { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
    val More = icon("more", fill = true) {
        for (y in listOf(6f, 12f, 18f)) {
            moveTo(13.4f, y); arcTo(1.4f, 1.4f, 0f, true, true, 10.6f, y); arcTo(1.4f, 1.4f, 0f, true, true, 13.4f, y); close()
        }
    }
    val Search = icon("search") {
        moveTo(17f, 10.5f); arcTo(6.5f, 6.5f, 0f, true, true, 4f, 10.5f); arcTo(6.5f, 6.5f, 0f, true, true, 17f, 10.5f)
        moveTo(15.4f, 15.4f); lineTo(20f, 20f)
    }
    val Settings = icon("settings") {
        // Three sliders: a quieter mark than a gear.
        moveTo(4f, 7f); lineTo(20f, 7f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 17f); lineTo(20f, 17f)
        moveTo(9.5f, 5f); lineTo(9.5f, 9f); moveTo(15f, 10f); lineTo(15f, 14f); moveTo(7.5f, 15f); lineTo(7.5f, 19f)
    }
    val Bookmark = icon("bookmark") { moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 20f); lineTo(12f, 16f); lineTo(7f, 20f); close() }
    val BookmarkFilled = icon("bookmark-filled", fill = true) { moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 20f); lineTo(12f, 16f); lineTo(7f, 20f); close() }
    val Check = icon("check", stroke = 1.8f) { moveTo(5f, 12.5f); lineTo(10f, 17f); lineTo(19f, 7f) }
    val Lock = icon("lock") {
        moveTo(6f, 11f); lineTo(18f, 11f); lineTo(18f, 20f); lineTo(6f, 20f); close()
        moveTo(8.5f, 11f); lineTo(8.5f, 8f); arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 8f); lineTo(15.5f, 11f)
    }
    val Share = icon("share") {
        moveTo(12f, 15f); lineTo(12f, 4f); moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f)
        moveTo(6f, 12f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 12f)
    }
    val Import = icon("import") {
        moveTo(12f, 4f); lineTo(12f, 15f); moveTo(8f, 11f); lineTo(12f, 15f); lineTo(16f, 11f)
        moveTo(6f, 12f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 12f)
    }
    val Book = icon("book") {
        moveTo(12f, 6.5f); curveTo(9.5f, 4.8f, 6.5f, 4.5f, 4f, 5f); lineTo(4f, 18.5f); curveTo(6.5f, 18f, 9.5f, 18.3f, 12f, 20f)
        curveTo(14.5f, 18.3f, 17.5f, 18f, 20f, 18.5f); lineTo(20f, 5f); curveTo(17.5f, 4.5f, 14.5f, 4.8f, 12f, 6.5f); close()
        moveTo(12f, 6.5f); lineTo(12f, 20f)
    }
    val Figure = icon("figure") {
        moveTo(14f, 5f); arcTo(2f, 2f, 0f, true, true, 10f, 5f); arcTo(2f, 2f, 0f, true, true, 14f, 5f)
        moveTo(12f, 8.5f); lineTo(12f, 14.5f); moveTo(7.5f, 10f); lineTo(16.5f, 10f); moveTo(12f, 14.5f); lineTo(9f, 20f); moveTo(12f, 14.5f); lineTo(15f, 20f)
    }
    val Review = icon("review") {
        moveTo(19.5f, 12f); arcTo(7.5f, 7.5f, 0f, true, true, 16.5f, 6f)
        moveTo(17f, 3f); lineTo(17f, 6.5f); lineTo(13.5f, 6.5f)
        moveTo(12f, 8.5f); lineTo(12f, 12f); lineTo(14.5f, 13.5f)
    }
    val Progress = icon("progress") {
        moveTo(5f, 20f); lineTo(5f, 13f); moveTo(10f, 20f); lineTo(10f, 9f); moveTo(15f, 20f); lineTo(15f, 11f); moveTo(20f, 20f); lineTo(20f, 5f)
    }
    val Pen = icon("pen") {
        moveTo(4f, 20f); lineTo(5f, 15.5f); lineTo(15.5f, 5f); lineTo(19f, 8.5f); lineTo(8.5f, 19f); close(); moveTo(13f, 7.5f); lineTo(16.5f, 11f)
    }
    val Phone = icon("phone") {
        moveTo(6f, 4f); lineTo(9.5f, 4f); lineTo(11f, 8.5f); lineTo(8.5f, 10f); curveTo(9.6f, 12.4f, 11.6f, 14.4f, 14f, 15.5f)
        lineTo(15.5f, 13f); lineTo(20f, 14.5f); lineTo(20f, 18f); curveTo(20f, 19.1f, 19.1f, 20f, 18f, 20f); curveTo(10.3f, 20f, 4f, 13.7f, 4f, 6f)
        curveTo(4f, 4.9f, 4.9f, 4f, 6f, 4f); close()
    }
    val Message = icon("message") {
        moveTo(5f, 5f); lineTo(19f, 5f); curveTo(19.6f, 5f, 20f, 5.4f, 20f, 6f); lineTo(20f, 15f); curveTo(20f, 15.6f, 19.6f, 16f, 19f, 16f)
        lineTo(10f, 16f); lineTo(6f, 19.5f); lineTo(6f, 16f); lineTo(5f, 16f); curveTo(4.4f, 16f, 4f, 15.6f, 4f, 15f); lineTo(4f, 6f)
        curveTo(4f, 5.4f, 4.4f, 5f, 5f, 5f); close()
    }
    val Heart = icon("care") {
        moveTo(12f, 19f); curveTo(5f, 14.5f, 3.5f, 11f, 4.2f, 8.2f); curveTo(5f, 5f, 9.3f, 4.2f, 12f, 7.6f)
        curveTo(14.7f, 4.2f, 19f, 5f, 19.8f, 8.2f); curveTo(20.5f, 11f, 19f, 14.5f, 12f, 19f); close()
    }
    val Diamond = icon("diamond", fill = true) { moveTo(12f, 5f); lineTo(19f, 12f); lineTo(12f, 19f); lineTo(5f, 12f); close() }
    val Eye = icon("eye") {
        moveTo(3f, 12f); curveTo(5.5f, 7.5f, 8.5f, 5.5f, 12f, 5.5f); curveTo(15.5f, 5.5f, 18.5f, 7.5f, 21f, 12f)
        curveTo(18.5f, 16.5f, 15.5f, 18.5f, 12f, 18.5f); curveTo(8.5f, 18.5f, 5.5f, 16.5f, 3f, 12f); close()
        moveTo(14.5f, 12f); arcTo(2.5f, 2.5f, 0f, true, true, 9.5f, 12f); arcTo(2.5f, 2.5f, 0f, true, true, 14.5f, 12f)
    }
    val Rotate = icon("rotate") {
        moveTo(4f, 12f); curveTo(4f, 8.5f, 7.6f, 6f, 12f, 6f); curveTo(16.4f, 6f, 20f, 8.5f, 20f, 12f)
        moveTo(20f, 12f); curveTo(20f, 15.5f, 16.4f, 18f, 12f, 18f); moveTo(9f, 15.5f); lineTo(12f, 18f); lineTo(9f, 20.5f)
    }
}
