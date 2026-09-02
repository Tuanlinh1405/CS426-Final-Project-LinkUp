package com.example.linkup.feature.reels

import android.content.Context
import com.example.linkup.data.network.ApiClient
import com.example.linkup.data.reels.Reel
import com.example.linkup.data.reels.ReelRepository
import com.example.linkup.feature.reels.player.ReelVideoCache
import kotlinx.coroutines.CancellationException

private const val STARTUP_REELS = 3
private const val AHEAD_REELS = 3
private const val BEHIND_REELS = 2

/** Called once after login so opening the Reels tab already has playable bytes on disk. */
suspend fun warmStartupReels(context: Context, repository: ReelRepository) {
    val page = try {
        repository.feed()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        return
    }
    warmReels(context, page.items.take(STARTUP_REELS))
}

/** A rolling window favors forward swipes but also keeps recent Reels quick to revisit. */
internal suspend fun warmAround(context: Context, reels: List<Reel>, currentIndex: Int) {
    if (reels.isEmpty()) return
    warmReels(context, warmIndices(currentIndex, reels.size).map(reels::get))
}

internal fun warmIndices(currentIndex: Int, itemCount: Int): List<Int> = buildList {
    for (offset in 1..AHEAD_REELS) add(currentIndex + offset)
    for (offset in 1..BEHIND_REELS) add(currentIndex - offset)
}.distinct().filter { it in 0 until itemCount }

private suspend fun warmReels(context: Context, reels: List<Reel>) {
    reels.forEach { reel ->
        ReelVideoCache.warm(context, ApiClient.mediaUrl(reel.videoUrl), reel.id)
    }
}
