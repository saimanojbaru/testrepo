package com.sortora.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject

/**
 * Decodes images at a reduced size suitable for on-device models.
 *
 * Uses power-of-two subsampling so huge photos never get fully decoded into memory; the result's
 * longest side lands between [maxDimension] and 2×[maxDimension].
 */
class BitmapLoader @Inject constructor(@ApplicationContext private val context: Context) {

    data class Loaded(val bitmap: Bitmap, val sourceWidth: Int, val sourceHeight: Int)

    /** Returns null when the format is not decodable on this device (e.g. HEIC on Android 8). */
    fun load(uri: Uri, maxDimension: Int): Loaded? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeModern(uri, maxDimension) else decodeLegacy(uri, maxDimension)
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    /** ImageDecoder handles HEIF/AVIF/WebP and applies EXIF orientation for us. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeModern(uri: Uri, maxDimension: Int): Loaded {
        var width = 0
        var height = 0
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            width = info.size.width
            height = info.size.height
            decoder.setTargetSampleSize(sampleSize(width, height, maxDimension))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return Loaded(bitmap, width, height)
    }

    private fun decodeLegacy(uri: Uri, maxDimension: Int): Loaded? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        // BitmapFactory ignores EXIF orientation; rotate so faces and text are upright.
        val degrees = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val upright = if (degrees == 0) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
                .also { if (it !== decoded) decoded.recycle() }
        }
        val swap = degrees == 90 || degrees == 270
        return Loaded(upright, if (swap) bounds.outHeight else bounds.outWidth, if (swap) bounds.outWidth else bounds.outHeight)
    }

    private fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
        val longest = maxOf(width, height)
        var sample = 1
        while (longest / (sample * 2) >= maxDimension) sample *= 2
        return sample
    }
}
