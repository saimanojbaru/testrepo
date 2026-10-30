package com.sortora.app.ui.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortora.app.R
import com.sortora.app.domain.repository.SettingsRepository
import com.sortora.app.ui.design.Art
import com.sortora.app.ui.design.GhostButton
import com.sortora.app.ui.design.Illustration
import com.sortora.app.ui.design.Motion
import com.sortora.app.ui.design.PrimaryButton
import com.sortora.app.ui.design.Radius
import com.sortora.app.ui.design.Sortora
import com.sortora.app.ui.design.Space
import com.sortora.app.ui.design.ambientLight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.absoluteValue

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    fun complete(then: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingComplete(true)
            then()
        }
    }
}

private data class Page(val art: Art, @StringRes val title: Int, @StringRes val body: Int)

private val pages = listOf(
    Page(Art.Sort, R.string.onboarding_1_title, R.string.onboarding_1_body),
    Page(Art.Shield, R.string.onboarding_2_title, R.string.onboarding_2_body),
    Page(Art.Review, R.string.onboarding_3_title, R.string.onboarding_3_body),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val c = Sortora.colors
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(c.canvas)
            .ambientLight(c)
            .safeDrawingPadding()
            .padding(horizontal = Space.xl, vertical = Space.md),
    ) {
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (!isLast) GhostButton(stringResource(R.string.action_skip), onClick = { viewModel.complete(onFinished) }, color = c.inkSecondary)
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Parallax: the art drifts slower than the page, the text fades with distance.
                Box(
                    Modifier.graphicsLayer {
                        val offset = pageOffset(pagerState, index)
                        translationX = offset * size.width * 0.45f
                        alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f) * 0.8f
                        val s = 1f - offset.absoluteValue.coerceIn(0f, 1f) * 0.12f
                        scaleX = s
                        scaleY = s
                    },
                ) { Illustration(page.art, size = 248.dp) }
                Spacer(Modifier.height(Space.xxl))
                Column(
                    Modifier
                        .widthIn(max = 380.dp)
                        .graphicsLayer {
                            val offset = pageOffset(pagerState, index)
                            alpha = 1f - offset.absoluteValue.coerceIn(0f, 1f)
                            translationX = offset * size.width * 0.15f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(page.title), style = MaterialTheme.typography.displaySmall, color = c.ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Space.md))
                    Text(stringResource(page.body), style = MaterialTheme.typography.bodyLarge, color = c.inkSecondary, textAlign = TextAlign.Center)
                }
            }
        }

        PageDots(pagerState, Modifier.align(Alignment.CenterHorizontally).padding(vertical = Space.xl))

        PrimaryButton(
            text = stringResource(if (isLast) R.string.action_get_started else R.string.action_next),
            onClick = {
                if (isLast) viewModel.complete(onFinished) else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1, animationSpec = Motion.gentle()) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.xs))
    }
}

private fun pageOffset(state: PagerState, page: Int): Float = (state.currentPage - page) + state.currentPageOffsetFraction

/** Dots that stretch into a pill for the current page, following the finger. */
@Composable
private fun PageDots(state: PagerState, modifier: Modifier = Modifier) {
    val c = Sortora.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(pages.size) { index ->
            Box(
                Modifier
                    .layout { m, cs ->
                        val closeness = (1f - pageOffset(state, index).absoluteValue).coerceIn(0f, 1f)
                        val w = (7.dp.toPx() + 18.dp.toPx() * closeness).toInt()
                        val p = m.measure(cs.copy(minWidth = w, maxWidth = w))
                        layout(w, p.height) { p.place(0, 0) }
                    }
                    .size(7.dp)
                    .clip(Radius.Pill)
                    .drawBehind { drawRect(lerp(c.hairline, c.ink, (1f - pageOffset(state, index).absoluteValue).coerceIn(0f, 1f))) },
            )
        }
    }
}
