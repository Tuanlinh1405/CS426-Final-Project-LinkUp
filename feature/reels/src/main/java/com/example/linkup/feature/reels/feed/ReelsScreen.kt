package com.example.linkup.feature.reels

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.linkup.core.ui.Avatar
import com.example.linkup.data.model.UserResponse
import com.example.linkup.data.network.ApiClient
import com.example.linkup.data.reels.*
import com.example.linkup.feature.reels.comments.ReelCommentsSheet
import com.example.linkup.feature.reels.player.ReelPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ReelsScreen(repository: ReelRepository, me: UserResponse?, onUpload: () -> Unit, onSignIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val items = remember { mutableStateListOf<Reel>() }
    var author by rememberSaveable { mutableStateOf<String?>(null) }
    var authorName by rememberSaveable { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var next by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var commentReel by remember { mutableStateOf<Reel?>(null) }
    var deleteReel by remember { mutableStateOf<Reel?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var muted by rememberSaveable { mutableStateOf(false) }
    val readyReels = remember { mutableStateMapOf<String, Boolean>() }
    val pager = rememberPagerState { items.size }
    fun replace(reel: Reel) { val index = items.indexOfFirst { it.id == reel.id }; if (index >= 0) items[index] = reel }
    fun action(id: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = id
        scope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Please retry." }
            finally { busy = null }
        }
    }
    LaunchedEffect(author, refresh, me?.id) {
        if (me == null) { items.clear(); next = null; loading = false; return@LaunchedEffect }
        loading = true; error = null; next = null; items.clear(); readyReels.clear()
        try {
            val page = repository.feed(author = author)
            items.addAll(page.items); next = page.nextCursor
            if (items.isNotEmpty()) pager.scrollToPage(0)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Cannot load reels." }
        finally { loading = false }
    }
    LaunchedEffect(pager.settledPage, items.size, next) {
        val cursor = next
        if (loading || loadingMore || cursor == null || pager.settledPage < items.size - 3) return@LaunchedEffect
        loadingMore = true
        try {
            val page = repository.feed(cursor, author)
            items.addAll(page.items.filter { reel -> items.none { it.id == reel.id } }); next = page.nextCursor
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Cannot load more reels."; next = null }
        finally { loadingMore = false }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (items.isNotEmpty()) VerticalPager(
            state = pager,
            key = { items[it].id },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val reel = items[index]
            val currentIndex = pager.settledPage
            val currentReady = items.getOrNull(currentIndex)?.id?.let { readyReels[it] == true } == true
            // Give the visible Reel all available bandwidth until it is playable. Once ready,
            // keep exactly one prepared player on either side for instant forward/back swipes.
            val preparePlayer = shouldPrepareReel(index, currentIndex, currentReady)
            Box(Modifier.fillMaxSize()) {
                if (preparePlayer) ReelPlayer(
                    ApiClient.mediaUrl(reel.videoUrl), reel.durationMs,
                    active = index == currentIndex && commentReel == null && deleteReel == null,
                    muted = muted, modifier = Modifier.fillMaxSize(),
                    onWatch = { repository.watch(reel.id, it) },
                    exitReason = { if (index != currentIndex) "SWIPE" else "BACKGROUND" },
                    onReady = { readyReels[reel.id] = true },
                ) else reel.thumbnailUrl?.let { AsyncImage(ApiClient.mediaUrl(it), "Video thumbnail", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f)))).padding(start = 18.dp, end = 82.dp, top = 60.dp, bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { author = reel.author.id; authorName = reel.author.username }) {
                        val avatarUrl = reel.author.avatarUrl
                        if (avatarUrl != null) AsyncImage(ApiClient.mediaUrl(avatarUrl), reel.author.name, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(CircleShape))
                        else Avatar(reel.author.initials, 36)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(reel.author.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("@${reel.author.username}", color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                    if (reel.caption.isNotBlank()) Text(reel.caption, color = Color.White, maxLines = 4, modifier = Modifier.padding(top = 10.dp))
                }
                Column(Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ReelAction(if (reel.liked) "♥" else "♡", reel.likeCount.toString(), if (reel.liked) Color(0xFFFF5078) else Color.White, busy == null) {
                        action(reel.id) { replace(repository.like(reel.id, !reel.liked)) }
                    }
                    ReelAction("💬", reel.commentCount.toString()) { commentReel = reel }
                    ReelAction("↗", "Share") {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${reel.caption}\n${ApiClient.mediaUrl(reel.videoUrl)}")
                        }, "Share reel"))
                    }
                    if (reel.author.id == me?.id) ReelAction("×", "Delete", enabled = busy == null) { deleteReel = reel }
                    else ReelAction("⊘", "Not interested", enabled = busy == null) { action(reel.id) { repository.hide(reel.id); items.removeAll { it.id == reel.id } } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .65f), Color.Transparent))).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Reels", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { author = if (author == me?.id) null else me?.id; authorName = if (author == null) null else "My reels" }) { Text(if (author == me?.id) "For you" else "My reels", color = Color.White) }
            TextButton(onClick = { muted = !muted }) { Text(if (muted) "Unmute" else "Mute", color = Color.White, fontSize = 11.sp) }
            TextButton(onClick = onUpload, enabled = me != null) { Text("＋", color = Color.White, fontSize = 28.sp) }
        }
        if (author != null) TextButton(onClick = { author = null; authorName = null }, modifier = Modifier.align(Alignment.TopStart).padding(top = 58.dp)) { Text("${authorName ?: "Creator"}  ×", color = Color.White) }
        if (items.isEmpty()) Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                me == null -> { Text("Sign in to watch and share reels.", color = Color.White); Button(onClick = onSignIn) { Text("Sign in") } }
                loading -> CircularProgressIndicator(color = Color.White)
                error == null -> { Text("No reels yet", color = Color.White, fontSize = 22.sp); Text("Share the first video, or refresh your feed.", color = Color.LightGray); Button(onClick = onUpload) { Text("Upload a reel") }; TextButton(onClick = { refresh++ }) { Text("Refresh") } }
            }
        }
        error?.let { message ->
            Column(Modifier.align(Alignment.Center).padding(24.dp).background(Color.Black.copy(alpha = .9f)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, color = Color.White)
                Row { TextButton(onClick = { refresh++ }) { Text("Refresh") }; TextButton(onClick = { error = null }) { Text("Dismiss") }; if (message.contains("sign in", true)) TextButton(onClick = onSignIn) { Text("Sign in") } }
            }
        }
        if (items.isNotEmpty() && !loading) TextButton(onClick = { refresh++ }, modifier = Modifier.align(Alignment.TopStart).padding(top = if (author == null) 50.dp else 92.dp)) { Text("↻", color = Color.White) }
    }
    commentReel?.let { reel -> ReelCommentsSheet(reel, me?.id.orEmpty(), repository, onDismiss = { commentReel = null }, onChanged = { scope.launch { runCatching { repository.get(reel.id) }.onSuccess(::replace) } }) }
    deleteReel?.let { reel -> AlertDialog(onDismissRequest = { deleteReel = null }, title = { Text("Delete this reel?") }, text = { Text("Its video, likes and comments will be removed.") },
        confirmButton = { TextButton(onClick = { deleteReel = null; action(reel.id) { repository.delete(reel.id); items.removeAll { it.id == reel.id } } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteReel = null }) { Text("Cancel") } }) }
}

@Composable private fun ReelAction(symbol: String, label: String, color: Color = Color.White, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.width(70.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(symbol, color = color, fontSize = 29.sp)
        Text(label, color = Color.White, fontSize = 10.sp, maxLines = 2)
    }
}
