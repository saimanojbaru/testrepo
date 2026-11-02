package com.nonverbal.mastery.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object PathRoute
@Serializable data class CategoryRoute(val categoryId: String)
@Serializable data class LevelRoute(val levelId: String, val section: Int = 0)
@Serializable data object PoseLibraryRoute
@Serializable data class PoseDetailRoute(val poseId: String, val compareWith: String? = null)
@Serializable data object ReviewsRoute
@Serializable data object ProgressRoute
@Serializable data object ReflectionsRoute
@Serializable data object SettingsRoute
@Serializable data object EthicsRoute
@Serializable data class CrisisRoute(val thenLevelId: String? = null)
