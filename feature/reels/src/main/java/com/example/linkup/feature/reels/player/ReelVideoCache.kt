package com.example.linkup.feature.reels.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One bounded cache shared by every Reel player and background warm-up request. */
@androidx.annotation.OptIn(UnstableApi::class)
object ReelVideoCache {
    private const val MAX_CACHE_BYTES = 300L * 1024 * 1024
    private const val WARM_BYTES = 3L * 1024 * 1024

    @Volatile private var cache: SimpleCache? = null
    // Two opening-byte downloads are enough to hide swipe latency without starving playback.
    private val warmSlots = Semaphore(2)
    private data class WarmLock(val mutex: Mutex = Mutex(), val users: AtomicInteger = AtomicInteger())
    private val warmLocks = ConcurrentHashMap<String, WarmLock>()
    private val activeWriters = ConcurrentHashMap<String, CacheWriter>()
    private val warmGeneration = AtomicLong(0)

    fun dataSourceFactory(context: Context): DataSource.Factory {
        val appContext = context.applicationContext
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val upstream = DefaultDataSource.Factory(appContext, http)
        val videoCache = runCatching { get(appContext) }.getOrNull() ?: return upstream
        return CacheDataSource.Factory()
            .setCache(videoCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Cache only the opening bytes. ExoPlayer fills the rest naturally while the Reel plays. */
    suspend fun warm(context: Context, uri: String, reelId: String) = withContext(Dispatchers.IO) {
        val generation = warmGeneration.get()
        val key = cacheKey(reelId)
        val reelLock = warmLocks.compute(key) { _, current ->
            (current ?: WarmLock()).also { it.users.incrementAndGet() }
        }!!
        try {
            // Deduplicate the same Reel before occupying a global network slot.
            reelLock.mutex.withLock {
                if (generation != warmGeneration.get()) return@withLock
                warmSlots.withPermit {
                    if (generation != warmGeneration.get()) return@withPermit
                    val source = dataSourceFactory(context).createDataSource() as? CacheDataSource ?: return@withPermit
                    val writer = CacheWriter(
                        source,
                        DataSpec.Builder().setUri(uri).setKey(key).setLength(WARM_BYTES).build(),
                        null,
                        null,
                    )
                    activeWriters[key] = writer
                    try { writer.cacheCancellable() }
                    finally { activeWriters.remove(key, writer) }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Warm-up is best effort; the visible player can still stream from the network.
        } finally {
            if (reelLock.users.decrementAndGet() == 0) warmLocks.remove(key, reelLock)
        }
    }

    /** Invalidates queued warm-ups and interrupts current ones when the user moves to a new window. */
    fun cancelWarmups() {
        warmGeneration.incrementAndGet()
        activeWriters.values.forEach(CacheWriter::cancel)
    }

    fun cacheKey(reelId: String): String = "reel:$reelId"

    @Synchronized
    private fun get(context: Context): SimpleCache {
        cache?.let { return it }
        return SimpleCache(
            context.cacheDir.resolve("reels-video-cache"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
    }

    private suspend fun CacheWriter.cacheCancellable() = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        try {
            cache()
            if (continuation.isActive) continuation.resume(Unit)
        } catch (error: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
