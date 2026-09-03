package com.linkup.ai

import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiImageOptimizerTest {
    @Test
    fun `large image is resized and encoded as a compact jpeg`() {
        val original = BufferedImage(3_200, 2_400, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().also { graphics ->
                graphics.color = Color(32, 96, 180)
                graphics.fillRect(0, 0, width, height)
                graphics.dispose()
            }
        }
        val sourceBytes = ByteArrayOutputStream().also { ImageIO.write(original, "png", it) }.toByteArray()

        val optimized = assertNotNull(AiImageOptimizer.optimize(GeminiImage("image/png", sourceBytes)))
        val decoded = assertNotNull(ImageIO.read(ByteArrayInputStream(optimized.bytes)))

        assertEquals("image/jpeg", optimized.mimeType)
        assertEquals(1_600, maxOf(decoded.width, decoded.height))
        assertTrue(optimized.bytes.size <= 1_500_000)
    }

    @Test
    fun `small format unsupported by ImageIO is preserved for Gemini`() {
        val original = GeminiImage("image/webp", byteArrayOf(1, 2, 3, 4))
        val optimized = assertNotNull(AiImageOptimizer.optimize(original))

        assertEquals(original.mimeType, optimized.mimeType)
        assertTrue(original.bytes.contentEquals(optimized.bytes))
    }
}
