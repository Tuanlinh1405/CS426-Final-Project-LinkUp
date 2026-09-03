package com.example.linkup.feature.reels.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTrackerTest {
    @Test fun `pause buffering background do not count as watch time`() {
        val tracker = PlaybackTracker(10000)
        tracker.tick(0, true); tracker.tick(500, false); tracker.tick(5500, false)
        tracker.tick(6000, true); tracker.tick(6250, false)
        assertEquals(750, tracker.watchedMs)
    }
    @Test fun `clock gaps and repeated loops are capped`() {
        val tracker = PlaybackTracker(1000)
        tracker.tick(0, true); tracker.tick(60000, true)
        assertEquals(1000, tracker.watchedMs)
        for (time in 61000L..70000L step 1000) tracker.tick(time, true)
        assertEquals(3000, tracker.watchedMs)
    }
    @Test fun `long videos can track beyond the old three minute cap`() {
        val tracker = PlaybackTracker(600000)
        tracker.tick(0, true)
        for (time in 1000L..240000L step 1000) tracker.tick(time, true)
        assertEquals(240000, tracker.watchedMs)
    }
}
