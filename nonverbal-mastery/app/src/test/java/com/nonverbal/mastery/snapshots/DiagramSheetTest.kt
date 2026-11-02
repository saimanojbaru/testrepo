package com.nonverbal.mastery.snapshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.nonverbal.mastery.design.components.Diagram
import com.nonverbal.mastery.design.components.DiagramKind
import com.nonverbal.mastery.design.theme.NonverbalTheme
import com.nonverbal.mastery.design.theme.Nv
import org.junit.Rule
import org.junit.Test

/** Renders every diagram and face plate at reading width, in both palettes, for visual review. */
class DiagramSheetTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 540, screenHeight = 720, density = Density.HIGH, softButtons = false),
        maxPercentDifference = 0.1,
    )

    private fun sheet(name: String, dark: Boolean, kinds: List<DiagramKind>) = paparazzi.snapshot(name + if (dark) "_ink" else "_paper") {
        NonverbalTheme(darkTheme = dark, reducedMotion = true) {
            Column(
                Modifier.fillMaxSize().background(Nv.colors.background).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) { kinds.forEach { Diagram(it) } }
        }
    }

    @Test fun faces() {
        DiagramKind.entries.filter { it.id.startsWith("face-") }.forEach { k ->
            sheet(k.id, false, listOf(k))
            sheet(k.id, true, listOf(k))
        }
    }

    @Test fun others() {
        val rest = DiagramKind.entries.filterNot { it.id.startsWith("face-") }
        rest.chunked(2).forEachIndexed { i, ks -> sheet("diagrams_$i", false, ks) }
    }
}
