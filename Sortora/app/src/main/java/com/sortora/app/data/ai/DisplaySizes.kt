package com.sortora.app.data.ai

import android.content.Context
import android.hardware.display.DisplayManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Physical pixel sizes of this device's displays (all supported modes). A picture with exactly
 * one of these sizes is very likely a screenshot taken on this phone.
 */
@Singleton
class DisplaySizes @Inject constructor(@ApplicationContext private val context: Context) {

    val sizes: List<Pair<Int, Int>> by lazy {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return@lazy emptyList()
        manager.displays
            .flatMap { display -> display.supportedModes.map { it.physicalWidth to it.physicalHeight } }
            .distinct()
    }
}
