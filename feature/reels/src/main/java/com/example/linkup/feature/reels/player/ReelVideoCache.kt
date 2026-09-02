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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** One bounded cache shared by every Reel player and background warm-up request. */
@androidx.annotation.OptIn(UnstableApi::class)
object ReelVideoCache {
    private const val MAX_CACHE_BYTES = 300L * 1024 * 1024
    private const val WARM_BYTES = 3L * 1024 * 1024

    @Volatile private var cache: SimpleCache? = null

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
        val factory = dataSourceFactory(context)
        val source = factory.createDataSource() as? CacheDataSource ?: return@withContext
        val writer = CacheWriter(
            source,
            DataSpec.Builder()
                .setUri(uri)
                .setKey(cacheKey(reelId))
                .setLength(WARM_BYTES)
                .build(),
            null,
            null,
        )
        runCatching {
            coroutineContext.ensureActive()
            writer.cache()
        }
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
}
