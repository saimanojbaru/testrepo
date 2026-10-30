@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.sortora.app.ui.faces

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import com.sortora.app.R
import com.sortora.app.domain.faces.FaceClustering
import com.sortora.app.domain.model.FaceRecord
import com.sortora.app.domain.naming.FileNameSanitizer
import com.sortora.app.domain.repository.FaceRepository
import com.sortora.app.domain.repository.SessionRepository
import com.sortora.app.ui.components.CenteredEmptyState
import com.sortora.app.ui.design.Art
import com.sortora.app.ui.design.Eyebrow
import com.sortora.app.ui.design.FluidSpinner
import com.sortora.app.ui.design.GhostButton
import com.sortora.app.ui.design.GlassTopBar
import com.sortora.app.ui.design.IconBubble
import com.sortora.app.ui.design.Motion
import com.sortora.app.ui.design.PageTitle
import com.sortora.app.ui.design.ScreenScaffold
import com.sortora.app.ui.design.SecondaryButton
import com.sortora.app.ui.design.SoftCard
import com.sortora.app.ui.design.Sortora
import com.sortora.app.ui.design.SortoraTextField
import com.sortora.app.ui.design.Space
import com.sortora.app.ui.design.hairline
import com.sortora.app.ui.design.isScrolled
import com.sortora.app.ui.design.pressable
import com.sortora.app.ui.design.scrolledPast
import com.sortora.app.ui.navigation.FaceGroupsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FaceCluster(
    val key: Long,
    val faces: List<FaceRecord>,
    val name: String?,
) {
    val itemIds: Set<Long> get() = faces.mapTo(LinkedHashSet()) { it.itemId }
}

data class FaceGroupsUiState(
    val loading: Boolean = true,
    val clusters: List<FaceCluster> = emptyList(),
    val singles: Int = 0,
    val totalFaces: Int = 0,
    val threshold: Float = FaceClustering.DEFAULT_THRESHOLD,
)

@HiltViewModel
class FaceGroupsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val faces: FaceRepository,
    private val sessions: SessionRepository,
) : ViewModel() {
    private val sessionId = savedStateHandle.toRoute<FaceGroupsRoute>().sessionId
    private val threshold = MutableStateFlow(FaceClustering.DEFAULT_THRESHOLD)
    private val messages = Channel<Int>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    private val faceFlow = faces.observeFaces(sessionId)

    /** The expensive similarity index is rebuilt only when the set of faces changes, not on renames. */
    private val indexFlow = faceFlow
        .distinctUntilChangedBy { list -> list.map { it.id } }
        .map { list -> list.map { it.id } to FaceClustering.index(list.map { it.embedding }) }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<FaceGroupsUiState> = combine(faceFlow, indexFlow, threshold) { list, (ids, index), t ->
        val byId = list.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        val groups = index.clusters(t).map { members -> members.map { ordered[it] } }
        val clusters = groups.filter { it.size > 1 }.map { members ->
            FaceCluster(members.first().id, members, FaceClustering.clusterName(members.map { it.personName }))
        }
        FaceGroupsUiState(
            loading = false,
            clusters = clusters,
            singles = groups.count { it.size == 1 },
            totalFaces = list.size,
            threshold = t,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FaceGroupsUiState())

    fun setThreshold(value: Float) {
        threshold.value = value
    }

    fun name(cluster: FaceCluster, name: String) {
        viewModelScope.launch { faces.setPersonName(cluster.faces.map { it.id }, name) }
    }

    /** Sends every file containing this face to People/<name>. */
    fun putInFolder(cluster: FaceCluster, fallbackName: String) {
        val person = FileNameSanitizer.sanitizeFolderSegment(cluster.name ?: fallbackName).ifEmpty { fallbackName }
        viewModelScope.launch {
            sessions.setCustomFolder(cluster.itemIds, "People/$person")
            messages.send(R.string.faces_moved_to_folder)
        }
    }
}

@Composable
fun FaceGroupsScreen(onBack: () -> Unit, viewModel: FaceGroupsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Sortora.colors
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val list = rememberLazyListState()
    val scrolled by list.isScrolled()
    val pastTitle by list.scrolledPast(1)
    LaunchedEffect(Unit) { viewModel.messageFlow.collect { snackbar.showSnackbar(resources.getString(it)) } }

    ScreenScaffold(
        snackbar = snackbar,
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.faces_title),
                showTitle = pastTitle,
                scrolled = scrolled,
                navigation = { IconBubble(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onBack) },
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                FluidSpinner(size = 34.dp)
                Spacer(Modifier.height(Space.md))
                Text(stringResource(R.string.faces_comparing), style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
            }
            return@ScreenScaffold
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item(key = "title") {
                PageTitle(stringResource(R.string.faces_title), eyebrow = stringResource(R.string.faces_eyebrow), subtitle = stringResource(R.string.faces_explainer))
            }
            item(key = "strictness") { StrictnessCard(state.threshold, viewModel::setThreshold) }
            if (state.clusters.isEmpty()) {
                item(key = "empty") {
                    CenteredEmptyState(Art.Faces, stringResource(R.string.faces_empty_title), stringResource(R.string.faces_empty_body), Modifier.animateItem())
                }
            }
            itemsIndexed(state.clusters, key = { _, cluster -> cluster.key }) { index, cluster ->
                val fallback = resources.getString(R.string.faces_person_n, index + 1)
                ClusterCard(
                    cluster = cluster,
                    fallbackName = fallback,
                    onName = { viewModel.name(cluster, it) },
                    onFolder = { viewModel.putInFolder(cluster, fallback) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (state.singles > 0) {
                item(key = "singles") {
                    Text(
                        pluralStringResource(R.plurals.faces_singles, state.singles, state.singles),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkTertiary,
                        modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs).animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StrictnessCard(threshold: Float, onChange: (Float) -> Unit) {
    val c = Sortora.colors
    var position by remember(threshold) { mutableFloatStateOf(threshold) }
    SoftCard(Modifier.padding(horizontal = Space.md).fillMaxWidth(), contentPadding = PaddingValues(Space.md)) {
        Text(stringResource(R.string.faces_strictness), style = MaterialTheme.typography.titleSmall, color = c.ink)
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onChange(position) },
            valueRange = 0.2f..0.8f,
            colors = SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.sunken,
                activeTickColor = c.onAccent,
                inactiveTickColor = c.inkTertiary,
            ),
        )
        Row {
            Text(stringResource(R.string.faces_looser), style = MaterialTheme.typography.labelMedium, color = c.inkSecondary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.faces_stricter), style = MaterialTheme.typography.labelMedium, color = c.inkSecondary)
        }
    }
}

private const val STACK_VISIBLE = 6
private const val GRID_COLUMNS = 5

/**
 * One look-alike group. Collapsed, the faces overlap in a row; expanded, they spread into a grid.
 * Every circle glides to its new place, and faces that join when the strictness changes pop in.
 */
@Composable
private fun ClusterCard(cluster: FaceCluster, fallbackName: String, onName: (String) -> Unit, onFolder: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sortora.colors
    var name by remember(cluster.key, cluster.name) { mutableStateOf(cluster.name.orEmpty()) }
    var expanded by remember(cluster.key) { mutableStateOf(false) }
    val spread by animateFloatAsState(if (expanded) 1f else 0f, Motion.gentle(), label = "spread")
    SoftCard(modifier.padding(horizontal = Space.md).fillMaxWidth(), contentPadding = PaddingValues(Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cluster.name ?: fallbackName, style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text(
                    pluralStringResource(R.plurals.faces_cluster_summary, cluster.itemIds.size, cluster.faces.size, cluster.itemIds.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSecondary,
                )
            }
            if (cluster.faces.size > STACK_VISIBLE) {
                GhostButton(
                    stringResource(if (expanded) R.string.faces_show_less else R.string.faces_show_all),
                    { expanded = !expanded },
                    leading = if (expanded) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        val faces = cluster.faces.take(60)
        LookaheadScope {
            Layout(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable({ expanded = !expanded }, enabled = cluster.faces.size > STACK_VISIBLE, pressedScale = 0.99f, haptic = null),
                content = {
                    faces.forEach { face ->
                        androidx.compose.runtime.key(face.id) {
                            FaceCircle(face, Modifier.animateBounds(this@LookaheadScope))
                        }
                    }
                    if (faces.size > STACK_VISIBLE) {
                        Box(
                            Modifier
                                .animateBounds(this@LookaheadScope)
                                .size(52.dp)
                                .graphicsLayer { alpha = 1f - spread }
                                .clip(CircleShape)
                                .background(c.sunken)
                                .hairline(CircleShape, c.hairline),
                            contentAlignment = Alignment.Center,
                        ) { Text("+${cluster.faces.size - STACK_VISIBLE + 1}", style = MaterialTheme.typography.labelMedium, color = c.inkSecondary) }
                    }
                },
            ) { measurables, constraints ->
                val circle = 52.dp.roundToPx()
                val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints.fixed(circle, circle)) }
                val hasMore = faces.size > STACK_VISIBLE
                val faceCount = faces.size
                val width = constraints.maxWidth
                if (expanded) {
                    val gap = ((width - circle * GRID_COLUMNS) / (GRID_COLUMNS - 1).coerceAtLeast(1)).coerceAtLeast(0)
                    val rows = (faceCount + GRID_COLUMNS - 1) / GRID_COLUMNS
                    val height = rows * circle + (rows - 1).coerceAtLeast(0) * gap
                    layout(width, height) {
                        placeables.forEachIndexed { i, p ->
                            if (i < faceCount) p.place((i % GRID_COLUMNS) * (circle + gap), (i / GRID_COLUMNS) * (circle + gap))
                            else p.place(width - circle, height - circle) // "+N" bubble fades out in the corner
                        }
                    }
                } else {
                    val overlap = 16.dp.roundToPx()
                    val step = circle - overlap
                    val visible = if (hasMore) STACK_VISIBLE - 1 else faceCount
                    layout(width, circle) {
                        placeables.forEachIndexed { i, p ->
                            when {
                                i < visible -> p.place(i * step, 0, zIndex = (faceCount - i).toFloat())
                                i < faceCount -> p.placeWithLayer((visible - 1).coerceAtLeast(0) * step, 0, zIndex = 0f) { alpha = 0f }
                                else -> p.place(visible * step, 0, zIndex = (faceCount + 1).toFloat())
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SortoraTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.faces_name_label),
                placeholder = fallbackName,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Space.xs))
            GhostButton(stringResource(R.string.action_save), { onName(name) }, enabled = name != cluster.name.orEmpty())
        }
        Spacer(Modifier.height(Space.sm))
        SecondaryButton(stringResource(R.string.faces_put_in_folder, cluster.name ?: fallbackName), onFolder, leading = Icons.Rounded.CreateNewFolder, compact = true)
    }
}

@Composable
private fun FaceCircle(face: FaceRecord, modifier: Modifier = Modifier) {
    val c = Sortora.colors
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.bouncy()) }
    Box(
        modifier
            .size(52.dp)
            .graphicsLayer {
                val a = appear.value
                scaleX = 0.4f + 0.6f * a
                scaleY = 0.4f + 0.6f * a
                alpha = a.coerceIn(0f, 1f)
            }
            .clip(CircleShape)
            .background(c.surface)
            .padding(2.dp)
            .clip(CircleShape)
            .background(c.thumbnail),
    ) {
        AsyncImage(model = File(face.thumbnailPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
