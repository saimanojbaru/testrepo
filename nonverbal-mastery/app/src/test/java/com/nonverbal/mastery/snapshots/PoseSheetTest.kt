package com.nonverbal.mastery.snapshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.nonverbal.mastery.data.models.Pose
import com.nonverbal.mastery.design.components.MarkerMode
import com.nonverbal.mastery.design.components.PoseFigure
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.theme.NonverbalTheme
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import java.io.File

class PoseSheetTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1000, screenWidth = 1000, xdpi = 320, ydpi = 320, softButtons = false),
        maxPercentDifference = 0.1,
    )

    private val poses: List<Pose> = json.decodeFromString(File("../content/poses.json").readText())

    private fun sheet(dark: Boolean, page: Int) = paparazzi.snapshot("p$page") {
        NonverbalTheme(darkTheme = dark) {
            Column(
                Modifier.fillMaxSize().background(Nv.colors.background).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                poses.drop(page * 6).take(6).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { pose ->
                            Column(Modifier.weight(1f)) {
                                Box(Modifier.fillMaxWidth().aspectRatio(0.7f).background(Nv.colors.surface)) {
                                    PoseFigure(pose, Modifier.fillMaxSize(), markers = MarkerMode.Dots)
                                }
                                Text(pose.title, style = Nv.type.uiSmall, color = Nv.colors.inkSecondary, maxLines = 2)
                            }
                        }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    @Test fun poseSheetLight() = (0..(poses.size - 1) / 6).forEach { sheet(false, it) }
    @Test fun poseSheetDark() = listOf(0, 15, 19).forEach { sheet(true, it) }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
