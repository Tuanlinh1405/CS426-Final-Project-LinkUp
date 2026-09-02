package com.example.linkup.feature.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.ui.Avatar
import com.example.linkup.core.ui.LinkUpField
import com.example.linkup.core.ui.PrimaryButton
import com.example.linkup.core.ui.ScreenHeader
import com.example.linkup.data.model.Reel
import com.example.linkup.data.model.User
import com.example.linkup.ui.theme.LinkMuted
import com.example.linkup.ui.theme.LinkPurple

import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@Composable
fun ReelsScreen(
    reels: List<Reel> = emptyList(),
    onUpload: () -> Unit,
    onProfile: () -> Unit
) {
    val reelItems = reels.ifEmpty {
        listOf(
            Reel(
                id = "demo",
                author = User("u1", "Sarah Jones", "@sarah.j", "SJ"),
                caption = "Exploring hidden places and collecting good memories ✨",
                likes = 12900,
                comments = 438,
                audioTitle = "original sound · Sarah"
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { reelItems.size })

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ReelItemPage(
                reel = reelItems[page],
                isCurrentPage = (page == pagerState.currentPage),
                onUpload = onUpload,
                onProfile = onProfile
            )
        }
    }
}

@Composable
private fun ReelItemPage(
    reel: Reel,
    isCurrentPage: Boolean,
    onUpload: () -> Unit,
    onProfile: () -> Unit
) {
    var liked by remember(reel.id) { mutableStateOf(reel.liked) }
    var likesCount by remember(reel.id) { mutableStateOf(reel.likes) }
    val thumb = reel.thumbnailUrl
    val thumbnailUrl = if (!thumb.isNullOrBlank()) {
        if (thumb.startsWith("http")) thumb 
        else "https://hoxujmjicfveykawiwvk.supabase.co/storage/v1/object/public/$thumb"
    } else null
    
    val video = reel.videoUrl
    val fullVideoUrl = if (video.isNotBlank()) {
        if (video.startsWith("http")) video
        else "https://hoxujmjicfveykawiwvk.supabase.co/storage/v1/object/public/$video"
    } else null

    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            fullVideoUrl?.let {
                setMediaItem(MediaItem.fromUri(it))
                prepare()
                repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF16363A), Color(0xFF16231D), Color.Black))
        )
    ) {
        if (fullVideoUrl != null) {
            AndroidView(
                factory = {
                    PlayerView(context).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = "Reel Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Add a slight gradient overlay to make text readable
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))
        ))
        Text("Reels", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.align(Alignment.TopStart).padding(18.dp))
        Text("＋", color = Color.White, fontSize = 34.sp, modifier = Modifier.align(Alignment.TopEnd).clickable(onClick = onUpload).padding(14.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp).fillMaxWidth(.78f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable(onClick = onProfile)) { Avatar(reel.author.initials, 38) }
                Text(" ${reel.author.name}", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            if (reel.caption.isNotBlank()) {
                Text(reel.caption, color = Color.White, maxLines = 4)
            }
            Text("♫ ${reel.audioTitle}", color = Color.White.copy(alpha = .7f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(18.dp))
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ReelAction(
                if (liked) "♥" else "♡",
                formatCount(likesCount)
            ) {
                liked = !liked
                likesCount += if (liked) 1 else -1
            }
            ReelAction("□", formatCount(reel.comments))
            ReelAction("↗", "Share")
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)), contentAlignment = Alignment.Center) { Text("♫", color = Color.White) }
        }
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}


@Composable
private fun ReelAction(symbol: String, label: String, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Text(symbol, color = Color.White, fontSize = 30.sp)
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
fun UploadReelScreen(me: User, onBack: () -> Unit, onPublished: () -> Unit) {
    var selected by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("Upload Reel", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth().height(330.dp).padding(16.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEAE7F0)).clickable { selected = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (selected) "▶" else "＋", color = LinkPurple, fontSize = 52.sp)
                    Text(if (selected) "demo-reel.mp4 · 00:18" else "Select a video", fontWeight = FontWeight.Bold)
                    Text(if (selected) "Tap to replace" else "MP4, up to 60 seconds", color = LinkMuted, fontSize = 12.sp)
                }
            }
            LinkUpField(caption, { caption = it }, "Caption", Modifier.padding(horizontal = 16.dp), singleLine = false)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(me.initials, 34)
                Text("  Publishing as ${me.name}", color = LinkMuted)
            }
        }
        PrimaryButton("Publish Reel", onPublished, Modifier.padding(16.dp), enabled = selected)
    }
}
