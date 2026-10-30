package com.sortora.app.ui.analysis

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sortora.app.R
import com.sortora.app.domain.model.AnalysisStatus
import com.sortora.app.domain.model.ItemProgress
import com.sortora.app.domain.model.SortSession
import com.sortora.app.domain.repository.BackgroundJobs
import com.sortora.app.domain.repository.JobProgress
import com.sortora.app.domain.repository.JobState
import com.sortora.app.domain.repository.SessionRepository
import com.sortora.app.ui.components.CenteredEmptyState
import com.sortora.app.ui.components.icon
import com.sortora.app.ui.components.labelRes
import com.sortora.app.ui.design.Art
import com.sortora.app.ui.design.Eyebrow
import com.sortora.app.ui.design.FluidRing
import com.sortora.app.ui.design.FluidSpinner
import com.sortora.app.ui.design.GhostButton
import com.sortora.app.ui.design.GlassDock
import com.sortora.app.ui.design.GlassTopBar
import com.sortora.app.ui.design.IconBubble
import com.sortora.app.ui.design.Motion
import com.sortora.app.ui.design.PrimaryButton
import com.sortora.app.ui.design.Reveal
import com.sortora.app.ui.design.ScreenScaffold
import com.sortora.app.ui.design.SecondaryButton
import com.sortora.app.ui.design.Sortora
import com.sortora.app.ui.design.SortoraDialog
import com.sortora.app.ui.design.Space
import com.sortora.app.ui.design.SuccessMark
import com.sortora.app.ui.design.morph
import com.sortora.app.ui.home.SORT_ORB_KEY
import com.sortora.app.ui.navigation.AnalysisRoute
import com.sortora.app.ui.sorting.SortingStage
import com.sortora.app.ui.sorting.StageItem
import com.sortora.app.ui.sorting.StageTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AnalysisUiState(
    val session: SortSession? = null,
    val job: JobProgress = JobProgress(JobState.IDLE),
    val items: List<ItemProgress> = emptyList(),
) {
    val processed: Int get() = (session?.analyzedItems ?: 0) + (session?.failedItems ?: 0)
    val total: Int get() = session?.totalItems ?: 0
    val progress: Float get() = if (total == 0) 0f else processed.toFloat() / total
    val finished: Boolean get() = total > 0 && processed >= total
    val stopped: Boolean get() = job.state == JobState.CANCELLED || job.state == JobState.FAILED
}

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sessions: SessionRepository,
    private val jobs: BackgroundJobs,
) : ViewModel() {
    val sessionId: Long = savedStateHandle.toRoute<AnalysisRoute>().sessionId

    val state: StateFlow<AnalysisUiState> = combine(
        sessions.observeSession(sessionId),
        jobs.observeAnalysis(sessionId),
        sessions.observeItemProgress(sessionId),
    ) { session, job, items -> AnalysisUiState(session, job, items) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisUiState())

    fun cancel() = jobs.cancelAnalysis(sessionId)

    fun retry() = jobs.startAnalysis(sessionId)
}

@Composable
fun AnalysisScreen(onBack: () -> Unit, onReview: (Long) -> Unit, viewModel: AnalysisViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Sortora.colors
    val resources = LocalResources.current
    var confirmCancel by remember { mutableStateOf(false) }
    val running = !state.finished && !state.stopped

    ScreenScaffold(
        ambient = true,
        topBar = {
            GlassTopBar(
                navigation = { IconBubble(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onBack) },
                actions = {
                    if (running) GhostButton(stringResource(R.string.analysis_stop), { confirmCancel = true }, color = c.inkSecondary, leading = Icons.Rounded.StopCircle)
                },
            )
        },
        bottomBar = {
            Reveal(visible = state.finished) {
                GlassDock {
                    PrimaryButton(stringResource(R.string.analysis_review_cta), { onReview(viewModel.sessionId) }, Modifier.fillMaxWidth())
                }
            }
        },
    ) { padding ->
        if (state.stopped && !state.finished) {
            val analyzed = state.session?.analyzedItems ?: 0
            CenteredEmptyState(
                art = Art.Paused,
                title = stringResource(if (state.job.state == JobState.FAILED) R.string.analysis_failed_title else R.string.analysis_stopped_title),
                message = pluralStringResource(R.plurals.analysis_stopped_body, analyzed, analyzed),
                modifier = Modifier.padding(padding),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    if (analyzed > 0) PrimaryButton(stringResource(R.string.analysis_review_partial), { onReview(viewModel.sessionId) })
                    SecondaryButton(stringResource(R.string.analysis_continue), viewModel::retry)
                }
            }
            return@ScreenScaffold
        }

        if (state.session == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { FluidSpinner(size = 34.dp) }
            return@ScreenScaffold
        }

        val byId = remember(state.items) { state.items.associateBy { it.id } }
        val waiting = remember(state.items) {
            state.items.filter { it.analysisStatus == AnalysisStatus.PENDING }.map { StageItem(it.id, it.uri, it.kind) }
        }
        val done = remember(state.items) {
            state.items.mapNotNull { item -> item.category?.takeIf { item.analysisStatus == AnalysisStatus.DONE }?.let { item.id to it.name } }.toMap()
        }
        val categories = remember(state.items) { state.items.mapNotNull { it.category?.takeIf { _ -> it.analysisStatus == AnalysisStatus.DONE } }.distinct() }
        val targets = categories.map { StageTarget(it.name, stringResource(it.labelRes()), it.icon()) }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow(stringResource(R.string.analysis_eyebrow))
            Spacer(Modifier.height(Space.xs))
            AnimatedContent(
                state.finished,
                transitionSpec = { fadeIn(Motion.fade(400)) togetherWith fadeOut(Motion.fade(200)) },
                label = "heading",
            ) { finished ->
                Text(
                    if (finished) stringResource(R.string.analysis_done_title) else pluralStringResource(R.plurals.analysis_heading, state.total, state.total),
                    style = MaterialTheme.typography.displaySmall,
                    color = c.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Space.gutter),
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                when {
                    state.finished -> pluralStringResource(R.plurals.analysis_done_body, state.processed, state.processed)
                    else -> state.job.currentFile ?: stringResource(
                        if (state.job.state == JobState.QUEUED) R.string.analysis_waiting else R.string.analysis_loading_models,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Space.xxl),
            )

            SortingStage(
                waiting = waiting,
                done = done,
                lookup = { id -> byId[id]?.let { StageItem(it.id, it.uri, it.kind) } },
                targets = targets,
                remainingLabel = { n -> resources.getQuantityString(R.plurals.stage_remaining, n, n) },
                center = { ProgressOrb(state) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Space.xl))
            Text(
                stringResource(R.string.analysis_background_hint),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = c.inkTertiary,
                modifier = Modifier.widthIn(max = 360.dp).padding(horizontal = Space.xxl),
            )
        }
    }

    if (confirmCancel) {
        SortoraDialog(
            onDismiss = { confirmCancel = false },
            title = stringResource(R.string.analysis_stop_confirm_title),
            text = stringResource(R.string.analysis_stop_confirm_body),
            confirm = stringResource(R.string.analysis_stop),
            onConfirm = { confirmCancel = false; viewModel.cancel() },
            dismiss = stringResource(R.string.action_keep_going),
        )
    }
}

/** The ring at the centre of the orbit; it becomes the success mark when everything is sorted. */
@Composable
private fun ProgressOrb(state: AnalysisUiState) {
    val c = Sortora.colors
    Box(Modifier.morph(SORT_ORB_KEY), contentAlignment = Alignment.Center) {
        AnimatedContent(
            state.finished,
            transitionSpec = { (fadeIn(Motion.fade(300)) + scaleIn(Motion.bouncy(), initialScale = 0.8f)) togetherWith (fadeOut(Motion.fade(200)) + scaleOut(targetScale = 1.08f)) },
            label = "orb",
        ) { finished ->
            if (finished) {
                SuccessMark(size = 148.dp)
            } else {
                FluidRing(state.progress, size = 148.dp, strokeWidth = 7.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.percent_value, (state.progress * 100).toInt()),
                            style = MaterialTheme.typography.headlineLarge,
                            color = c.ink,
                        )
                        Text(
                            "${state.processed} / ${state.total}",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.inkTertiary,
                        )
                    }
                }
            }
        }
    }
}
