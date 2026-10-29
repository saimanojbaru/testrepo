package com.sortora.app.domain

import com.sortora.app.domain.model.AnalysisResult
import com.sortora.app.domain.model.AnalysisStatus
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DetectedLabel
import com.sortora.app.domain.model.MediaFile
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.domain.model.MediaMetadata
import com.sortora.app.domain.model.SessionItem
import com.sortora.app.domain.model.TextSummary
import java.time.LocalDateTime
import java.time.ZoneOffset

val UTC: ZoneOffset = ZoneOffset.UTC

fun millis(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0, s: Int = 0): Long =
    LocalDateTime.of(y, mo, d, h, mi, s).toInstant(UTC).toEpochMilli()

fun label(text: String, confidence: Float = 0.9f) = DetectedLabel(text, confidence)

fun item(
    id: Long,
    name: String,
    category: ContentCategory,
    tags: List<String> = emptyList(),
    labels: List<DetectedLabel> = emptyList(),
    date: Long? = millis(2025, 10, 12, 14, 30, 15),
    kind: MediaKind = MediaKind.IMAGE,
    title: String? = null,
    keywords: List<String> = emptyList(),
    customName: String? = null,
    customFolder: String? = null,
    included: Boolean = true,
) = SessionItem(
    id = id,
    sessionId = 1,
    file = MediaFile("content://test/$id", name, if (kind == MediaKind.IMAGE) "image/jpeg" else "video/mp4", 1000, null, kind),
    analysisStatus = AnalysisStatus.DONE,
    analysis = AnalysisResult(
        category = category,
        confidence = 0.8f,
        labels = labels,
        text = TextSummary(title = title, keywords = keywords),
        metadata = MediaMetadata(captureTimeMillis = date),
        tags = tags,
    ),
    error = null,
    customName = customName,
    customFolder = customFolder,
    included = included,
    plannedName = null,
    plannedFolder = null,
    applyStatus = null,
    applyMessage = null,
)
