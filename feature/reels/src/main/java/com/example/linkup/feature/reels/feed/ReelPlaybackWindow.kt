package com.example.linkup.feature.reels

import kotlin.math.abs

/**
 * The visible Reel is prepared immediately. Its direct neighbours only start buffering after it
 * is ready so preloading never delays the video the user is waiting for.
 */
internal fun shouldPrepareReel(index: Int, currentIndex: Int, currentReady: Boolean): Boolean =
    index == currentIndex || (currentReady && abs(index - currentIndex) == 1)
