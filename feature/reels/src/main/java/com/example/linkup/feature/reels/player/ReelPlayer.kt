package com.example.linkup.feature.reels.player

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.linkup.data.reels.WatchEvent
import kotlinx.coroutines.delay
import java.util.UUID

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun ReelPlayer(
    uri: String, durationMs: Long, active: Boolean, muted: Boolean, modifier: Modifier = Modifier,
    onWatch: ((WatchEvent) -> Unit)? = null, exitReason: () -> String = { "BACKGROUND" },
    onReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var paused by remember(uri) { mutableStateOf(false) }
    var buffering by remember(uri) { mutableStateOf(true) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var position by remember(uri) { mutableFloatStateOf(0f) }
    var scrubPosition by remember(uri) { mutableFloatStateOf(0f) }
    var scrubbing by remember(uri) { mutableStateOf(false) }
    val watcher by rememberUpdatedState(onWatch)
    val finalReason by rememberUpdatedState(exitReason)
    val readyCallback by rememberUpdatedState(onReady)
    val tracker = remember(uri) { PlaybackTracker(durationMs) }
    val sessionId = remember(uri) { UUID.randomUUID().toString() }
    var started by remember(uri) { mutableStateOf(false) }
    val player = remember(uri) {
        val dataSource = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val mediaSource = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSource)
            .setLoadControl(loadControl)
            .build()
            .apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(uri)); prepare()
        }
    }
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!resumed) { tracker.tick(SystemClock.elapsedRealtime(), false); player.pause() }
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                tracker.tick(SystemClock.elapsedRealtime(), isPlaying)
                if (isPlaying && !started) { started = true; watcher?.invoke(WatchEvent(sessionId, 0, "START")) }
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) readyCallback()
            }
            override fun onPlayerError(failure: PlaybackException) { error = "Video could not play. Check your connection or try another video." }
        }
        lifecycle.addObserver(observer); player.addListener(listener)
        onDispose {
            tracker.tick(SystemClock.elapsedRealtime(), false)
            if (started) watcher?.invoke(WatchEvent(sessionId, tracker.watchedMs, finalReason()))
            lifecycle.removeObserver(observer); player.removeListener(listener); player.release()
        }
    }
    LaunchedEffect(active, resumed, paused, muted, player) {
        player.volume = if (muted) 0f else 1f
        player.playWhenReady = active && resumed && !paused
        if (!player.playWhenReady) {
            tracker.tick(SystemClock.elapsedRealtime(), false)
            if (started) watcher?.invoke(WatchEvent(sessionId, tracker.watchedMs, if (resumed) "PAUSE" else "BACKGROUND"))
        }
    }
    LaunchedEffect(player) {
        var lastReport = SystemClock.elapsedRealtime()
        while (true) {
            val now = SystemClock.elapsedRealtime()
            if (player.isPlaying && !started) {
                started = true; watcher?.invoke(WatchEvent(sessionId, 0, "START"))
            }
            tracker.tick(now, player.isPlaying)
            if (!scrubbing) {
                position = (player.currentPosition.toFloat() / resolvedDuration(player.duration, durationMs)).coerceIn(0f, 1f)
            }
            if (started && now - lastReport >= 5000) {
                watcher?.invoke(WatchEvent(sessionId, tracker.watchedMs)); lastReport = now
            }
            delay(250)
        }
    }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; this.player = player } },
            update = { it.keepScreenOn = active && resumed && !paused; it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.matchParentSize().pointerInput(player, durationMs) {
            detectTapGestures(
                onTap = { paused = !paused },
                onDoubleTap = { offset ->
                    val duration = resolvedDuration(player.duration, durationMs)
                    val delta = if (offset.x < size.width / 2f) -DOUBLE_TAP_SEEK_MS else DOUBLE_TAP_SEEK_MS
                    val target = seekPosition(player.currentPosition, duration, delta)
                    player.seekTo(target)
                    position = target.toFloat() / duration
                },
            )
        })
        if (buffering && error == null) CircularProgressIndicator(color = Color.White)
        if (paused && error == null) Text("▶", color = Color.White, fontSize = 48.sp)
        error?.let { message ->
            Column(Modifier.background(Color.Black.copy(alpha = .75f)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, color = Color.White)
                TextButton(onClick = { error = null; player.prepare(); paused = false }) { Text("Retry", color = Color.White) }
            }
        }
        Slider(
            value = if (scrubbing) scrubPosition else position,
            onValueChange = {
                scrubbing = true
                scrubPosition = it
            },
            onValueChangeFinished = {
                val duration = resolvedDuration(player.duration, durationMs)
                val target = (duration * scrubPosition).toLong().coerceIn(0, duration)
                player.seekTo(target)
                position = scrubPosition
                scrubbing = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = .25f),
            ),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp),
        )
    }
}

private const val MIN_BUFFER_MS = 2_000
private const val MAX_BUFFER_MS = 15_000
private const val BUFFER_FOR_PLAYBACK_MS = 750
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_500
private const val DOUBLE_TAP_SEEK_MS = 10_000L
