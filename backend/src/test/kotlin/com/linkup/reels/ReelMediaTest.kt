package com.linkup.reels

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ReelMediaTest {
    @Test fun `ranges support explicit suffix and open ended requests`() {
        assertEquals(ByteRange(0, 9), ByteRange.parse("bytes=0-9", 100))
        assertEquals(ByteRange(90, 99), ByteRange.parse("bytes=-10", 100))
        assertEquals(ByteRange(50, 99), ByteRange.parse("bytes=50-", 100))
        assertNull(ByteRange.parse(null, 100))
    }
    @Test fun `invalid and multiple ranges are rejected`() {
        listOf("bytes=200-", "bytes=-0", "bytes=9-1", "bytes=0-1,2-3").forEach { value ->
            val error = assertThrows(ReelFailure::class.java) { ByteRange.parse(value, 100) }
            assertEquals(416, error.status)
        }
    }
    @Test fun `upload stream limit is enforced without reading unlimited input`() {
        assertThrows(ReelFailure::class.java) { ReelMedia.copyLimited(ByteArrayInputStream(ByteArray(101)), ByteArrayOutputStream(), 100) }
        assertEquals(100, ReelMedia.copyLimited(ByteArrayInputStream(ByteArray(100)), ByteArrayOutputStream(), 100))
    }
}
