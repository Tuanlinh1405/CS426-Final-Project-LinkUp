package com.example.linkup.feature.feed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

internal suspend fun preparePostImages(context: Context, uris: List<Uri>): List<File> = withContext(Dispatchers.IO) {
    val prepared = mutableListOf<File>()
    try {
        uris.forEach { uri ->
            val bitmap = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                ?: error("Cannot decode one of the selected photos.")
            val longest = max(bitmap.width, bitmap.height)
            val outputBitmap = if (longest > 2048) {
                val scale = 2048f / longest
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                    .also { bitmap.recycle() }
            } else bitmap
            val file = File.createTempFile("linkup-post-", ".jpg", context.cacheDir)
            try {
                file.outputStream().use { output -> check(outputBitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "Cannot prepare selected photo." } }
                check(file.length() in 1..10L * 1024 * 1024) { "Each prepared photo must be 10 MB or smaller." }
                prepared += file
            } catch (error: Throwable) { file.delete(); throw error }
            finally { outputBitmap.recycle() }
        }
        prepared
    } catch (error: Throwable) {
        prepared.forEach(File::delete)
        throw error
    }
}
