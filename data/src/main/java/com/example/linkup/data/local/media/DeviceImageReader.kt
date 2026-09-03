package com.example.linkup.data.local.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.example.linkup.data.model.PickedImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a picked `content://` image into upload-ready JPEG bytes.
 *
 * Camera photos are routinely 12 MP and several megabytes. Decoding one at full
 * size to upload a 96 dp avatar wastes memory and risks an OOM on low-end
 * devices, so the bitmap is subsampled while decoding, rotated to match its EXIF
 * orientation, then re-compressed.
 */
@Singleton
class DeviceImageReader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val AVATAR_MAX_DIMENSION = 512
        const val COVER_MAX_DIMENSION = 1440
        const val CHAT_MAX_DIMENSION = 1600
        private const val JPEG_QUALITY = 85
    }

    /** Returns null when the URI cannot be opened or does not decode to an image. */
    suspend fun read(uri: Uri, maxDimension: Int, fileName: String): PickedImage? =
        withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext null

            val oriented = applyExifRotation(uri, decoded)
            val scaled = scaleWithin(oriented, maxDimension)

            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            if (scaled !== decoded) scaled.recycle()
            if (oriented !== decoded && oriented !== scaled) oriented.recycle()
            decoded.recycle()

            PickedImage(
                bytes = output.toByteArray(),
                mimeType = "image/jpeg",
                fileName = fileName
            )
        }

    /** Largest power-of-two subsample that still covers [maxDimension]. */
    private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxDimension) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleWithin(source: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxDimension) return source
        val ratio = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    /** Photos taken in portrait carry rotation in EXIF rather than in the pixels. */
    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            bitmap
        }
    }
}
