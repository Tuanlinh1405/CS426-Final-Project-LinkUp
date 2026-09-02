package com.example.linkup.feature.reels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelPlaybackWindowTest {
    @Test
    fun `visible reel is prepared before it is ready`() {
        assertTrue(shouldPrepareReel(index = 3, currentIndex = 3, currentReady = false))
        assertFalse(shouldPrepareReel(index = 2, currentIndex = 3, currentReady = false))
        assertFalse(shouldPrepareReel(index = 4, currentIndex = 3, currentReady = false))
    }

    @Test
    fun `previous and next reels are prepared after visible reel is ready`() {
        assertTrue(shouldPrepareReel(index = 2, currentIndex = 3, currentReady = true))
        assertTrue(shouldPrepareReel(index = 3, currentIndex = 3, currentReady = true))
        assertTrue(shouldPrepareReel(index = 4, currentIndex = 3, currentReady = true))
    }

    @Test
    fun `reels outside adjacent window are not prepared`() {
        assertFalse(shouldPrepareReel(index = 1, currentIndex = 3, currentReady = true))
        assertFalse(shouldPrepareReel(index = 5, currentIndex = 3, currentReady = true))
    }
}
