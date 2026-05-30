package com.hitit.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hitit.app.MainActivity
import com.hitit.app.data.repository.RepRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate

/**
 * Home-screen widget showing today's Rep progress. Glance widgets can't be Hilt-injected directly,
 * so dependencies are pulled via a Hilt [EntryPoint] resolved from the application context.
 */
class HitItWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun repRepository(): RepRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .repRepository()
        val snapshot = repo.todaySnapshot(LocalDate.now())

        provideContent {
            WidgetContent(done = snapshot.done, total = snapshot.total)
        }
    }

    @Composable
    private fun WidgetContent(done: Int, total: Int) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF16161D)))
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Hit it",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF33E1FF)),
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = if (total == 0) "No reps today" else "$done / $total reps done",
                style = TextStyle(color = ColorProvider(Color(0xFFECECF1))),
            )
        }
    }
}
