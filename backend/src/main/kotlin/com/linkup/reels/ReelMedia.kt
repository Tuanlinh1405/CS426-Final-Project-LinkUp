package com.linkup.reels

import org.jcodec.common.Codec
import org.jcodec.common.io.NIOUtils
import org.jcodec.containers.mp4.demuxer.MP4Demuxer
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

object ReelMedia {
    const val MAX_VIDEO_BYTES = 50L * 1024 * 1024
    fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return total
            total += count
            if (total > limit) throw ReelFailure(413, "Selected file is too large.")
            output.write(buffer, 0, count)
        }
    }

    fun inspect(file: Path): VideoMetadata {
        if (Files.size(file) !in 16..MAX_VIDEO_BYTES) throw ReelFailure(400, "Select a non-empty MP4 video up to 50 MB.")
        try {
            Files.newInputStream(file).use { input ->
                val header = input.readNBytes(12)
                if (String(header, 4, 4, Charsets.US_ASCII) != "ftyp") throw ReelFailure(400, "Only MP4 videos are supported.")
            }
            NIOUtils.readableChannel(file.toFile()).use { channel ->
                val demuxer = MP4Demuxer.createMP4Demuxer(channel)
                val metadata = demuxer.videoTrack?.meta ?: throw ReelFailure(400, "The file has no video track.")
                if (metadata.codec != Codec.H264) throw ReelFailure(400, "Use an MP4 encoded with H.264 for compatible playback.")
                if (!metadata.totalDuration.isFinite() || metadata.totalDuration <= 0) throw ReelFailure(400, "Video duration must be greater than 0.")
                val size = metadata.videoCodecMeta?.size ?: throw ReelFailure(400, "Cannot read video dimensions.")
                if (size.width !in 1..4096 || size.height !in 1..4096 || metadata.totalFrames <= 0) throw ReelFailure(400, "Invalid video dimensions or frames.")
                return VideoMetadata((metadata.totalDuration * 1000).toLong().coerceAtLeast(1), size.width, size.height)
            }
        } catch (error: ReelFailure) { throw error }
        catch (_: Exception) { throw ReelFailure(400, "Cannot read this MP4. Export as H.264 MP4 and try again.") }
    }

    fun validateThumbnail(file: Path) {
        if (Files.size(file) !in 1..1048576) throw ReelFailure(400, "Thumbnail must be a JPEG under 1 MB.")
        ImageIO.createImageInputStream(file.toFile()).use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) throw ReelFailure(400, "Invalid thumbnail.")
            val reader = readers.next()
            try {
                reader.input = stream
                if (!reader.formatName.equals("JPEG", true) || reader.getWidth(0) !in 1..2048 || reader.getHeight(0) !in 1..2048) throw ReelFailure(400, "Thumbnail must be a JPEG up to 2048 pixels.")
            } finally { reader.dispose() }
        }
    }
}

data class ByteRange(val start: Long, val end: Long) {
    val length: Long get() = end - start + 1
    companion object {
        fun parse(value: String?, size: Long): ByteRange? {
            if (value == null) return null
            try {
                require(size > 0 && value.startsWith("bytes=") && !value.contains(','))
                val parts = value.removePrefix("bytes=").split('-'); require(parts.size == 2)
                val start: Long
                val end: Long
                if (parts[0].isEmpty()) {
                    val suffix = parts[1].toLong(); require(suffix > 0)
                    start = (size - suffix).coerceAtLeast(0); end = size - 1
                } else {
                    start = parts[0].toLong(); end = parts[1].takeIf(String::isNotEmpty)?.toLong()?.coerceAtMost(size - 1) ?: (size - 1)
                }
                require(start >= 0 && start < size && end >= start)
                return ByteRange(start, end)
            } catch (_: Exception) { throw ReelFailure(416, "Requested range is not available.") }
        }
    }
}
