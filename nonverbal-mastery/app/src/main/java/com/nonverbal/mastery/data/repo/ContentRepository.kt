package com.nonverbal.mastery.data.repo

import android.content.Context
import com.nonverbal.mastery.data.models.Category
import com.nonverbal.mastery.data.models.ContentPack
import com.nonverbal.mastery.data.models.Level
import com.nonverbal.mastery.data.models.Pose
import com.nonverbal.mastery.data.models.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.zip.GZIPInputStream

/** A chapter in a category's table of contents, written or not yet written. */
data class ContentsEntry(
    val index: Int,
    val title: String,
    val tier: Tier,
    val level: Level?,
)

data class PoseUse(val levelId: String, val levelTitle: String, val categoryId: String, val asPair: Boolean)

/** The loaded pack, indexed for the screens. Immutable once built. */
class Curriculum(val pack: ContentPack) {
    val categories: List<Category> = pack.categories
    val levels: Map<String, Level> = pack.categories.flatMap { it.levels }.associateBy { it.id }
    val poses: Map<String, Pose> = pack.poses.associateBy { it.id }
    val categoryById: Map<String, Category> = categories.associateBy { it.id }

    /** Planned chapters per category: authored + outline, 15 by default. */
    val plannedPerCategory: Map<String, Int> = categories.associate { c ->
        c.id to maxOf(c.outline.size, c.levels.maxOfOrNull { it.index } ?: 0, c.levels.size)
    }
    val plannedTotal: Int = plannedPerCategory.values.sum()
    val writtenTotal: Int = levels.size

    fun contents(categoryId: String): List<ContentsEntry> {
        val c = categoryById[categoryId] ?: return emptyList()
        val byIndex = c.levels.associateBy { it.index }
        val indices = (c.outline.map { it.index } + c.levels.map { it.index }).toSortedSet()
        return indices.map { i ->
            val level = byIndex[i]
            ContentsEntry(i, level?.title ?: c.outline.first { it.index == i }.title, level?.tier ?: tierFor(i), level)
        }
    }

    fun levelsInOrder(categoryId: String): List<Level> = categoryById[categoryId]?.levels?.sortedBy { it.index } ?: emptyList()

    fun categoryNumber(categoryId: String): Int = categories.indexOfFirst { it.id == categoryId } + 1

    /** "1.11" style reference for a level. */
    fun reference(level: Level): String = "${categoryNumber(level.categoryId)}.${level.index}"

    val poseUsage: Map<String, List<PoseUse>> by lazy {
        val out = mutableMapOf<String, MutableList<PoseUse>>()
        for (level in levels.values) {
            for (b in level.teach) b.poseId?.let { out.getOrPut(it) { mutableListOf() } += PoseUse(level.id, level.title, level.categoryId, false) }
            for (p in level.poseGallery) {
                for (id in listOf(p.doPoseId, p.dontPoseId)) out.getOrPut(id) { mutableListOf() } += PoseUse(level.id, level.title, level.categoryId, true)
            }
        }
        out.mapValues { (_, v) -> v.distinctBy { it.levelId } }
    }

    companion object {
        fun tierFor(index: Int): Tier = when (index) {
            in 1..3 -> Tier.FOUNDATION
            in 4..6 -> Tier.INTERMEDIATE
            in 7..9 -> Tier.ADVANCED
            in 10..12 -> Tier.EXPERT
            else -> Tier.MASTER
        }
    }
}

sealed interface ContentState {
    data object Loading : ContentState
    data class Ready(val curriculum: Curriculum) : ContentState
    data class Failed(val reason: String) : ContentState
}

class ContentRepository(private val context: Context) {
    private val _state = MutableStateFlow<ContentState>(ContentState.Loading)
    val state: StateFlow<ContentState> = _state.asStateFlow()

    suspend fun load() {
        if (_state.value is ContentState.Ready) return
        _state.value = runCatching { ContentState.Ready(Curriculum(read())) }
            .getOrElse { ContentState.Failed(it.message ?: "unknown") }
    }

    private suspend fun read(): ContentPack = withContext(Dispatchers.IO) {
        val assets = context.assets
        val names = assets.list("content")?.toSet().orEmpty()
        val text = if ("pack.json.gz" in names) {
            GZIPInputStream(assets.open("content/pack.json.gz")).bufferedReader().use { it.readText() }
        } else {
            assets.open("content/pack.json").bufferedReader().use { it.readText() }
        }
        decode(text)
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true }
        fun decode(text: String): ContentPack = json.decodeFromString(ContentPack.serializer(), text)
    }
}
