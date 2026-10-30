package com.sortora.app.ui.apply

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.sortora.app.domain.model.ApplyStatus
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.SessionItem
import com.sortora.app.domain.model.SessionStatus
import com.sortora.app.domain.model.SortSession
import com.sortora.app.domain.repository.BackgroundJobs
import com.sortora.app.domain.repository.JobProgress
import com.sortora.app.domain.repository.JobState
import com.sortora.app.domain.repository.SessionRepository
import com.sortora.app.domain.usecase.ApplySummary
import com.sortora.app.ui.design.Eyebrow
import com.sortora.app.ui.design.FluidRing
import com.sortora.app.ui.design.FluidSpinner
import com.sortora.app.ui.design.GlassDock
import com.sortora.app.ui.design.GlassTopBar
import com.sortora.app.ui.design.Motion
import com.sortora.app.ui.design.PrimaryButton
import com.sortora.app.ui.design.Reveal
import com.sortora.app.ui.design.ScreenScaffold
import com.sortora.app.ui.design.SecondaryButton
import com.sortora.app.ui.design.SoftCard
import com.sortora.app.ui.design.Sortora
import com.sortora.app.ui.design.Space
import com.sortora.app.ui.design.SuccessMark
import com.sortora.app.ui.navigation.ApplyRoute
import com.sortora.app.ui.sorting.SortingStage
import com.sortora.app.ui.sorting.StageItem
import com.sortora.app.ui.sorting.StageTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ApplyUiState(
    val session: SortSession? = null,
    val job: JobProgress = JobProgress(JobState.IDLE),
    val planned: List<SessionItem> = emptyList(),
    val total: Int = 0,
    val done: Int = 0,
    val summary: ApplySummary = ApplySummary(0, 0, 0),
    val problems: List<SessionItem> = emptyList(),
) {
    val finished: Boolean
        get() = session?.status == SessionStatus.APPLIED || job.state == JobState.SUCCEEDED || job.state == JobState.FAILED
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total
}

@HiltViewModel
class ApplyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sessions: SessionRepository,
    jobs: BackgroundJobs,
) : ViewModel() {
    private val sessionId = savedStateHandle.toRoute<ApplyRoute>().sessionId

    val state: StateFlow<ApplyUiState> = combine(
        sessions.observeSession(sessionId),
        sessions.observeItems(sessionId),
        jobs.observeApply(sessionId),
    ) { session, items, job ->
        val planned = items.filter { it.plannedName != null }
        ApplyUiState(
            session = session,
            job = job,
            planned = planned,
            total = planned.size,
            done = planned.count { it.applyStatus != null },
            summary = ApplySummary.of(planned.map { it.applyStatus }),
            problems = planned.filter { it.applyStatus == ApplyStatus.FAILED || it.applyStatus == ApplyStatus.COPIED_ORIGINAL_KEPT },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApplyUiState())
}

private const val OTHER = "\u0000other"
private const val RENAMED = "\u0000renamed"
private const val MAX_PILES = 8

@Composable
fun ApplyScreen(onDone: () -> Unit, onOpenHistory: () -> Unit, viewModel: ApplyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Sortora.colors
    val resources = LocalResources.current
    val mode = state.session?.mode ?: OperationMode.COPY

    // Piles: the busiest folders by plan, everything else in one "other" pile.
    val pileOf: (SessionItem) -> String = remember(state.planned, mode) { pileMapper(state.planned, mode) }
    val waiting = remember(state.planned) { state.planned.filter { it.applyStatus == null }.map { StageItem(it.id, it.file.uri, it.file.kind) } }
    val done = remember(state.planned, pileOf) { state.planned.filter { it.applyStatus != null }.associate { it.id to pileOf(it) } }
    val byId = remember(state.planned) { state.planned.associateBy { it.id } }
    val targets = remember(state.planned, pileOf) {
        state.planned.map(pileOf).distinct().map { key ->
            when (key) {
                OTHER -> StageTarget(key, resources.getString(R.string.apply_other_folders))
                RENAMED -> StageTarget(key, resources.getString(R.string.apply_renamed_pile), Icons.Rounded.DriveFileRenameOutline)
                else -> StageTarget(key, key.substringAfterLast('/').ifEmpty { key })
            }
        }
    }

    ScreenScaffold(
        ambient = true,
        topBar = { GlassTopBar() },
        bottomBar = {
            Reveal(visible = state.finished) {
                GlassDock {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        SecondaryButton(stringResource(R.string.apply_view_log), onOpenHistory, Modifier.weight(1f))
                        PrimaryButton(stringResource(R.string.action_done), onDone, Modifier.weight(1f))
                    }
                }
            }
        },
    ) { padding ->
        if (state.session == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { FluidSpinner(size = 34.dp) }
            return@ScreenScaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Eyebrow(
                stringResource(
                    when (mode) {
                        OperationMode.COPY -> R.string.apply_eyebrow_copy
                        OperationMode.MOVE -> R.string.apply_eyebrow_move
                        OperationMode.RENAME -> R.string.apply_eyebrow_rename
                    },
                ),
            )
            Spacer(Modifier.height(Space.xs))
            AnimatedContent(state.finished, transitionSpec = { fadeIn(Motion.fade(400)) togetherWith fadeOut(Motion.fade(200)) }, label = "heading") { finished ->
                Text(
                    stringResource(
                        when {
                            finished -> R.string.apply_title_done
                            mode == OperationMode.MOVE -> R.string.apply_title_moving
                            mode == OperationMode.RENAME -> R.string.apply_title_renaming
                            else -> R.string.apply_title_copying
                        },
                    ),
                    style = MaterialTheme.typography.displaySmall,
                    color = c.ink,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                if (state.finished) stringResource(R.string.apply_log_saved_undo) else state.job.currentFile ?: stringResource(R.string.apply_background_hint),
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSecondary,
                maxLines = 2,
                overflow = TextOverflow.MiddleEllipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Space.xxl),
            )

            SortingStage(
                waiting = waiting,
                done = done,
                lookup = { id -> byId[id]?.let { StageItem(it.id, it.file.uri, it.file.kind) } },
                targets = targets,
                remainingLabel = { n -> resources.getQuantityString(R.plurals.stage_remaining, n, n) },
                center = {
                    AnimatedContent(
                        state.finished,
                        transitionSpec = { (fadeIn(Motion.fade(300)) + scaleIn(Motion.bouncy(), initialScale = 0.8f)) togetherWith (fadeOut(Motion.fade(200)) + scaleOut(targetScale = 1.08f)) },
                        label = "orb",
                    ) { finished ->
                        if (finished) {
                            SuccessMark(size = 148.dp, tone = if (state.summary.failed > 0 && state.summary.succeeded == 0) c.danger else c.accent)
                        } else {
                            FluidRing(state.progress, size = 148.dp, strokeWidth = 7.dp) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.percent_value, (state.progress * 100).toInt()), style = MaterialTheme.typography.headlineLarge, color = c.ink)
                                    Text("${state.done} / ${state.total}", style = MaterialTheme.typography.labelMedium, color = c.inkTertiary)
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(state.finished, enter = fadeIn(Motion.fade(400)) + expandVertically(Motion.gentle())) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.md)) {
                        state.job.error?.let { ResultRow(Icons.Rounded.ErrorOutline, c.danger, stringResource(R.string.apply_stopped, it)) }
                        ResultRow(
                            Icons.Rounded.CheckCircle,
                            c.success,
                            pluralStringResource(
                                when (mode) {
                                    OperationMode.COPY -> R.plurals.apply_copied
                                    OperationMode.MOVE -> R.plurals.apply_moved
                                    OperationMode.RENAME -> R.plurals.apply_renamed
                                },
                                state.summary.succeeded, state.summary.succeeded,
                            ),
                        )
                        if (state.summary.keptOriginal > 0) {
                            ResultRow(Icons.Rounded.Info, c.warning, pluralStringResource(R.plurals.apply_kept_original, state.summary.keptOriginal, state.summary.keptOriginal))
                        }
                        if (state.summary.failed > 0) {
                            ResultRow(Icons.Rounded.ErrorOutline, c.danger, pluralStringResource(R.plurals.apply_failed, state.summary.failed, state.summary.failed))
                        }
                    }
                    if (state.problems.isNotEmpty()) {
                        Eyebrow(stringResource(R.string.apply_problems), Modifier.padding(start = Space.xxs, top = Space.sm))
                        state.problems.forEach { item ->
                            SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.md)) {
                                Text(item.file.displayName, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                                Text(item.applyMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.inkSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun pileMapper(planned: List<SessionItem>, mode: OperationMode): (SessionItem) -> String {
    if (mode == OperationMode.RENAME) return { RENAMED }
    val top = planned.groupingBy { it.plannedFolder.orEmpty() }.eachCount().entries
        .sortedByDescending { it.value }.take(MAX_PILES).map { it.key }.toSet()
    return { item -> item.plannedFolder.orEmpty().takeIf { it in top } ?: OTHER }
}

@Composable
private fun ResultRow(icon: ImageVector, tint: Color, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.sm))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Sortora.colors.ink)
    }
}
