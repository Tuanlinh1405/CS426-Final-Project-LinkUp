package com.example.linkup.feature.reels.player

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class ReelSeekTest {
    @Test
    fun `double tap seek stays inside video bounds`() {
        assertEquals(0, seekPosition(3_000, 60_000, -10_000))
        assertEquals(25_000, seekPosition(15_000, 60_000, 10_000))
        assertEquals(60_000, seekPosition(55_000, 60_000, 10_000))
    }

    @Test
    fun `metadata duration is used until player reports duration`() {
        assertEquals(45_000, resolvedDuration(C.TIME_UNSET, 45_000))
        assertEquals(42_000, resolvedDuration(42_000, 45_000))
    }
}
