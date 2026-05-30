package com.hitit.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Hosts [HitItWidget]. The receiver is a BroadcastReceiver, so Hilt could inject here if needed. */
class HitItWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HitItWidget()
}
