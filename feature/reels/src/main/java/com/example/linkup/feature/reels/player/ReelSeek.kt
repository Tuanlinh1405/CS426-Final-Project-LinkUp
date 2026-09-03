package com.example.linkup.feature.reels.player

import androidx.media3.common.C

internal fun resolvedDuration(playerDurationMs: Long, metadataDurationMs: Long): Long =
    playerDurationMs.takeIf { it != C.TIME_UNSET && it > 0 }
        ?: metadataDurationMs.coerceAtLeast(1)

internal fun seekPosition(currentPositionMs: Long, durationMs: Long, deltaMs: Long): Long =
    (currentPositionMs + deltaMs).coerceIn(0, durationMs.coerceAtLeast(0))
