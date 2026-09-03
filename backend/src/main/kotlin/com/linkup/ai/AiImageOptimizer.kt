package com.linkup.ai

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.roundToInt

/** Shrinks social photos before Base64 encoding so Gemini never receives multi-megabyte originals. */
internal object AiImageOptimizer {
    private const val MAX_EDGE = 1_600
    private const val MAX_OUTPUT_BYTES = 1_500_000
    private const val MAX_UNSUPPORTED_IMAGE_BYTES = 3_000_000

    fun optimize(image: GeminiImage): GeminiImage? {
        val source = runCatching { ImageIO.read(ByteArrayInputStream(image.bytes)) }.getOrNull()
            ?: return image.takeIf { it.bytes.size <= MAX_UNSUPPORTED_IMAGE_BYTES }

        var rendered = renderRgb(source, MAX_EDGE)
        var encoded = encodeJpeg(rendered, 0.80f) ?: return null
        var attempts = 0
        while (encoded.size > MAX_OUTPUT_BYTES && attempts < 4 && maxOf(rendered.width, rendered.height) > 720) {
            rendered = renderRgb(rendered, (maxOf(rendered.width, rendered.height) * 0.82).roundToInt())
            encoded = encodeJpeg(rendered, (0.74f - attempts * 0.06f).coerceAtLeast(0.54f)) ?: return null
            attempts++
        }
        return GeminiImage("image/jpeg", encoded)
    }

    private fun renderRgb(source: BufferedImage, maxEdge: Int): BufferedImage {
        val scale = minOf(1.0, maxEdge.toDouble() / maxOf(source.width, source.height).coerceAtLeast(1))
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        target.createGraphics().use { graphics ->
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, width, height)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(source, 0, 0, width, height, null)
        }
        return target
    }

    private fun encodeJpeg(image: BufferedImage, quality: Float): ByteArray? {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").asSequence().firstOrNull() ?: return null
        return try {
            val bytes = ByteArrayOutputStream()
            MemoryCacheImageOutputStream(bytes).use { output ->
                writer.output = output
                val parameters = writer.defaultWriteParam.apply {
                    compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = quality
                }
                writer.write(null, IIOImage(image, null, null), parameters)
            }
            bytes.toByteArray()
        } finally {
            writer.dispose()
        }
    }

    private inline fun <T : java.awt.Graphics2D, R> T.use(block: (T) -> R): R = try {
        block(this)
    } finally {
        dispose()
    }
}
