package com.example.linkup.feature.reels.player

/** Counts only foreground, actively-playing time; buffering, pausing and background time are excluded. */
class PlaybackTracker(private val durationMs: Long) {
    var watchedMs: Long = 0; private set
    private var lastTime: Long? = null
    private var wasPlaying = false
    fun tick(nowMs: Long, isPlaying: Boolean) {
        val previous = lastTime
        val duration = durationMs.coerceAtLeast(1)
        val replayLimit = if (duration > Long.MAX_VALUE / 3) Long.MAX_VALUE else duration * 3
        if (previous != null && wasPlaying) watchedMs = (watchedMs + (nowMs - previous).coerceIn(0, 1000)).coerceAtMost(replayLimit)
        lastTime = nowMs
        wasPlaying = isPlaying
    }
}
