package com.example.linkup.feature.feed

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PostTimeTest {
    private val now = Instant.parse("2026-09-02T12:00:00Z")
    @Test fun `formats recent post times`() {
        assertEquals("Now", relativePostTime("2026-09-02T11:59:30Z", now))
        assertEquals("15m", relativePostTime("2026-09-02T11:45:00Z", now))
        assertEquals("2h", relativePostTime("2026-09-02T10:00:00Z", now))
        assertEquals("2d", relativePostTime("2026-08-31T12:00:00Z", now))
    }
}
