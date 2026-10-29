package com.sortora.app.domain.model

import kotlinx.serialization.Serializable

/** Broad kind of a media file. Screenshots are images; they are detected during analysis. */
@Serializable
enum class MediaKind { IMAGE, VIDEO }

/**
 * A file the user picked, as seen through the Storage Access Framework.
 *
 * URIs are kept as plain strings so the domain layer stays free of Android types.
 */
@Serializable
data class MediaFile(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long?,
    val kind: MediaKind,
    /** Best-effort folder path such as "Pictures/Screenshots" (only some providers expose it). */
    val pathHint: String? = null,
    /** MediaStore's DATE_TAKEN, when the file came from the media library. */
    val dateTakenMillis: Long? = null,
) {
    val extension: String
        get() = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()

    val baseName: String
        get() = if ('.' in displayName) displayName.substringBeforeLast('.') else displayName
}

/** Where a capture date came from, from most to least trustworthy. */
@Serializable
enum class DateSource { EXIF, CONTAINER, FILENAME, MEDIA_STORE, FILE_MODIFIED, NONE }

/** Technical metadata read from EXIF (images) or the container (videos). */
@Serializable
data class MediaMetadata(
    val captureTimeMillis: Long? = null,
    val dateSource: DateSource = DateSource.NONE,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val software: String? = null,
    val userComment: String? = null,
    val hasAudio: Boolean? = null,
) {
    val hasCameraInfo: Boolean get() = !cameraMake.isNullOrBlank() || !cameraModel.isNullOrBlank()

    val aspectRatio: Float?
        get() {
            val w = width ?: return null
            val h = height ?: return null
            if (w <= 0 || h <= 0) return null
            return maxOf(w, h).toFloat() / minOf(w, h).toFloat()
        }
}
