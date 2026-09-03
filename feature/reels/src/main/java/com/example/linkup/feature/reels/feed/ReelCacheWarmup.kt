package com.example.linkup.feature.reels

import android.content.Context
import com.example.linkup.data.network.ApiClient
import com.example.linkup.data.reels.Reel
import com.example.linkup.data.reels.ReelRepository
import com.example.linkup.feature.reels.player.ReelVideoCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
    warmReels(context, page.items.take(STARTUP_REELS), parallelism = 2)
}

/** A rolling window favors forward swipes but also keeps recent Reels quick to revisit. */
internal suspend fun warmAround(context: Context, reels: List<Reel>, currentIndex: Int) {
    if (reels.isEmpty()) return
    // A swipe makes queued work for the previous window useless. Active CacheWriters are cancelled.
    ReelVideoCache.cancelWarmups()
    // The adjacent players already fill the shared cache. Use one low-priority stream only for
    // farther Reels so background work never opens duplicate connections for +/- 1.
    warmReels(context, warmIndices(currentIndex, reels.size).map(reels::get), parallelism = 1)
}

internal fun warmIndices(currentIndex: Int, itemCount: Int): List<Int> = buildList {
    for (offset in 2..AHEAD_REELS) add(currentIndex + offset)
    for (offset in 2..BEHIND_REELS) add(currentIndex - offset)
}.distinct().filter { it in 0 until itemCount }

private suspend fun warmReels(context: Context, reels: List<Reel>, parallelism: Int) = supervisorScope {
    // ReelVideoCache owns a global two-slot gate, so startup and rolling windows cannot flood IO.
    val slots = Semaphore(parallelism)
    reels.distinctBy(Reel::id).map { reel ->
        async { slots.withPermit { ReelVideoCache.warm(context, ApiClient.mediaUrl(reel.videoUrl), reel.id) } }
    }.awaitAll()
}
