package com.example.linkup.feature.reels.upload

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

data class SelectedVideo(val file: File, val durationMs: Long, val width: Int, val height: Int, val thumbnails: List<File>) {
    fun cleanup() { file.delete(); thumbnails.forEach { it.delete() } }
}

suspend fun prepareVideo(context: Context, uri: Uri): SelectedVideo = withContext(Dispatchers.IO) {
    val video = File.createTempFile("reel-upload-", ".mp4", context.cacheDir)
    val thumbnails = mutableListOf<File>()
    try {
        context.contentResolver.openInputStream(uri)?.use { input -> video.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024); var total = 0L
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer); if (count < 0) break
                total += count; require(total <= 50L * 1024 * 1024) { "Video must be under 50 MB." }
                output.write(buffer, 0, count)
            }
        } } ?: error("Cannot open this video. Select it again.")
        require(video.length() > 0) { "The selected video is empty." }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: error("Cannot read video duration.")
            require(duration > 0) { "Choose a video with a valid duration." }
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            require(width > 0 && height > 0) { "The selected file has no video track." }
            for (fraction in listOf(.1, .3, .5, .8)) {
                coroutineContext.ensureActive()
                val frame = retriever.getFrameAtTime((duration * 1000 * fraction).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                try {
                    val scale = 320f / maxOf(frame.width, frame.height)
                    val small = Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt().coerceAtLeast(1), (frame.height * scale).toInt().coerceAtLeast(1), true)
                    try {
                        val image = File.createTempFile("reel-cover-", ".jpg", context.cacheDir)
                        thumbnails += image
                        image.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    } finally { if (small !== frame) small.recycle() }
                } finally { frame.recycle() }
            }
            SelectedVideo(video, duration, width, height, thumbnails.toList())
        } finally { retriever.release() }
    } catch (error: Throwable) { video.delete(); thumbnails.forEach { it.delete() }; throw error }
}
