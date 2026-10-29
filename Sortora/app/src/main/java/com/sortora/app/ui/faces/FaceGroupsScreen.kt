@file:OptIn(ExperimentalMaterial3Api::class)

package com.sortora.app.ui.faces

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
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
import com.sortora.app.ui.components.EmptyState
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
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messageFlow.collect { snackbar.showSnackbar(resources.getString(it)) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.faces_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.faces_comparing), style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.faces_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { StrictnessSlider(state.threshold, viewModel::setThreshold) }
            if (state.clusters.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Face,
                        title = stringResource(R.string.faces_empty_title),
                        message = stringResource(R.string.faces_empty_body),
                    )
                }
            }
            items(state.clusters, key = { it.key }) { cluster ->
                val index = state.clusters.indexOf(cluster) + 1
                val fallback = resources.getString(R.string.faces_person_n, index)
                ClusterCard(
                    cluster = cluster,
                    fallbackName = fallback,
                    onName = { viewModel.name(cluster, it) },
                    onFolder = { viewModel.putInFolder(cluster, fallback) },
                )
            }
            if (state.singles > 0) {
                item {
                    Text(
                        pluralStringResource(R.plurals.faces_singles, state.singles, state.singles),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StrictnessSlider(threshold: Float, onChange: (Float) -> Unit) {
    var position by remember(threshold) { mutableFloatStateOf(threshold) }
    Column {
        Text(stringResource(R.string.faces_strictness), style = MaterialTheme.typography.bodyLarge)
        Slider(value = position, onValueChange = { position = it }, onValueChangeFinished = { onChange(position) }, valueRange = 0.2f..0.8f)
        Row {
            Text(stringResource(R.string.faces_looser), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.faces_stricter), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ClusterCard(cluster: FaceCluster, fallbackName: String, onName: (String) -> Unit, onFolder: () -> Unit) {
    var name by remember(cluster.key, cluster.name) { mutableStateOf(cluster.name.orEmpty()) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(12.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(cluster.faces.take(12), key = { it.id }) { face ->
                    AsyncImage(
                        model = File(face.thumbnailPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                pluralStringResource(R.plurals.faces_cluster_summary, cluster.itemIds.size, cluster.faces.size, cluster.itemIds.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(fallbackName) },
                    label = { Text(stringResource(R.string.faces_name_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onName(name) }, enabled = name != cluster.name.orEmpty()) { Text(stringResource(R.string.action_save)) }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onFolder) {
                Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.faces_put_in_folder, cluster.name ?: fallbackName))
            }
        }
    }
}
