package com.sortora.app.data.media

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.core.net.toUri
import com.sortora.app.domain.model.DateSource
import com.sortora.app.domain.model.MediaFile
import com.sortora.app.domain.model.MediaMetadata
import com.sortora.app.domain.naming.DateParsing
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject

/** Reads EXIF metadata and works out the best available capture date. */
class MetadataReader @Inject constructor(@ApplicationContext private val context: Context) {

    fun readImage(file: MediaFile): MediaMetadata {
        val exif = openExif(file.uri.toUri())
        val exifDate = exif?.let {
            DateParsing.parseExif(it.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                ?: DateParsing.parseExif(it.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED))
                ?: DateParsing.parseExif(it.getAttribute(ExifInterface.TAG_DATETIME))
        }
        val (date, source) = if (exifDate != null) exifDate to DateSource.EXIF else fallbackDate(file)

        val rotated = exif?.rotationDegrees.let { it == 90 || it == 270 }
        val w = exif?.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)?.takeIf { it > 0 }
            ?: exif?.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0)?.takeIf { it > 0 }
        val h = exif?.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)?.takeIf { it > 0 }
            ?: exif?.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0)?.takeIf { it > 0 }

        return MediaMetadata(
            captureTimeMillis = date,
            dateSource = source,
            width = if (rotated) h else w,
            height = if (rotated) w else h,
            cameraMake = exif?.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.takeIf { it.isNotEmpty() },
            cameraModel = exif?.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.takeIf { it.isNotEmpty() },
            software = exif?.getAttribute(ExifInterface.TAG_SOFTWARE),
            userComment = exif?.getAttribute(ExifInterface.TAG_USER_COMMENT),
        )
    }

    /** Video date: container date, then file name, then modification time. */
    fun resolveVideoDate(file: MediaFile, containerDate: String?): Pair<Long?, DateSource> {
        DateParsing.parseContainerDate(containerDate)?.let { return it to DateSource.CONTAINER }
        return fallbackDate(file)
    }

    fun fallbackDate(file: MediaFile): Pair<Long?, DateSource> {
        DateParsing.parseFromFileName(file.displayName)?.let { return it to DateSource.FILENAME }
        file.dateTakenMillis?.takeIf { it > 0 }?.let { return it to DateSource.MEDIA_STORE }
        file.lastModifiedMillis?.takeIf { it > 0 }?.let { return it to DateSource.FILE_MODIFIED }
        return null to DateSource.NONE
    }

    private fun openExif(uri: Uri): ExifInterface? = try {
        // A file descriptor lets ExifInterface seek, which HEIF/MP4-style containers need.
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd -> ExifInterface(pfd.fileDescriptor) }
    } catch (_: IOException) {
        streamExif(uri)
    } catch (_: IllegalArgumentException) {
        streamExif(uri)
    } catch (_: UnsupportedOperationException) {
        streamExif(uri)
    }

    private fun streamExif(uri: Uri): ExifInterface? = try {
        context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
    } catch (_: Exception) {
        null
    }
}
