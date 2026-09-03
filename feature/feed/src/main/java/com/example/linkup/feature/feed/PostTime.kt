package com.example.linkup.feature.feed

import java.time.Duration
import java.time.Instant

internal fun relativePostTime(value: String, now: Instant = Instant.now()): String = runCatching {
    val duration = Duration.between(Instant.parse(value), now)
    when {
        duration.isNegative || duration.seconds < 60 -> "Now"
        duration.toMinutes() < 60 -> "${duration.toMinutes()}m"
        duration.toHours() < 24 -> "${duration.toHours()}h"
        duration.toDays() < 7 -> "${duration.toDays()}d"
        else -> "${duration.toDays() / 7}w"
    }
}.getOrDefault(value)
