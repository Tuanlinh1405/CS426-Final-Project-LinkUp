package com.example.linkup.feature.feed

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.example.linkup.core.ui.Avatar
import com.example.linkup.core.ui.LinkUpField
import com.example.linkup.core.ui.LinkUpTopBar
import com.example.linkup.core.ui.PrimaryButton
import com.example.linkup.core.ui.ScreenHeader
import com.example.linkup.data.feed.*
import com.example.linkup.data.model.UserResponse
import com.example.linkup.data.network.ApiClient
import com.example.linkup.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun FeedScreen(
    me: UserResponse?,
    repository: PostRepository,
    onCreatePost: () -> Unit,
    onOpenPost: (String) -> Unit,
    onProfile: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onAi: () -> Unit,
    onSignIn: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val posts = remember { mutableStateListOf<FeedPost>() }
    val listState = rememberLazyListState()
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyPost by remember { mutableStateOf<String?>(null) }
    fun replace(post: FeedPost) { posts.indexOfFirst { it.id == post.id }.takeIf { it >= 0 }?.let { posts[it] = post } }
    fun share(post: FeedPost) {
        val media = post.media.firstOrNull()?.let { ApiClient.mediaUrl("media/${it.id}") }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, listOfNotNull(post.content.takeIf(String::isNotBlank), media).joinToString("\n"))
        }, "Share post"))
    }
    LaunchedEffect(me?.id, refresh) {
        if (me == null) { posts.clear(); loading = false; return@LaunchedEffect }
        loading = true; error = null; nextCursor = null
        try {
            val page = repository.feed()
            posts.clear(); posts.addAll(page.items); nextCursor = page.nextCursor
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Cannot load feed." }
        finally { loading = false }
    }
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= posts.size - 3 } == true } }
    val visiblePostIndex by remember { derivedStateOf { (listState.firstVisibleItemIndex - 1).coerceAtLeast(0) } }
    LaunchedEffect(visiblePostIndex, posts.size) {
        // Let visible composables request first, then warm the same disk/memory cache for nearby posts.
        delay(250)
        posts.drop(visiblePostIndex).take(3).mapNotNull { it.media.firstOrNull() }.forEach { media ->
            context.imageLoader.enqueue(feedImageRequest(context, media))
        }
    }
    LaunchedEffect(nearEnd, nextCursor, posts.size) {
        val cursor = nextCursor
        if (!nearEnd || cursor == null || loading || loadingMore) return@LaunchedEffect
        loadingMore = true
        try {
            val page = repository.feed(cursor)
            posts.addAll(page.items.filter { incoming -> posts.none { it.id == incoming.id } })
            nextCursor = page.nextCursor
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Cannot load more posts." }
        finally { loadingMore = false }
    }
    Column(Modifier.fillMaxSize().background(LinkCanvas)) {
        LinkUpTopBar(onSearch, onNotifications, onAi)
        if (me == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Sign in to view your feed.")
                Button(onClick = onSignIn) { Text("Sign in") }
            }
            return@Column
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().background(Color.White).clickable(onClick = onCreatePost).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.clickable(onClick = onProfile)) { UserAvatar(null, userInitials(me), 42) }
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(LinkCanvas).padding(horizontal = 16.dp, vertical = 12.dp)) { Text("What's on your mind?", color = LinkMuted) }
                    Text("＋", color = LinkPurple, fontSize = 24.sp)
                }
                Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
                    TextButton(onClick = { refresh++ }, enabled = !loading) { Text("Refresh") }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (loading && posts.isEmpty()) item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            if (!loading && posts.isEmpty() && error == null) item {
                Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No posts yet", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Share the first update with your community.", color = LinkMuted)
                    Button(onClick = onCreatePost) { Text("Create post") }
                }
            }
            items(posts, key = { it.id }) { post ->
                PostCard(post, onOpen = { onOpenPost(post.id) }, onLike = {
                    if (busyPost == null) {
                        busyPost = post.id
                        scope.launch {
                            try { replace(repository.like(post.id, !post.liked)) }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "Cannot update like." }
                            finally { busyPost = null }
                        }
                    }
                }, onShare = { share(post) }, enabled = busyPost == null)
                Spacer(Modifier.height(8.dp))
            }
            if (loadingMore) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun PostCard(
    post: FeedPost,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onShare: () -> Unit,
    enabled: Boolean = true,
    showAllMedia: Boolean = false,
    onDelete: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(post.author.avatarUrl, post.author.initials, 42)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(post.author.name, fontWeight = FontWeight.Bold)
                Text("@${post.author.username} · ${relativePostTime(post.createdAt)}", color = LinkMuted, fontSize = 12.sp)
            }
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
        if (post.content.isNotBlank()) Text(post.content, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).clickable(onClick = onOpen), lineHeight = 21.sp)
        if (post.media.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            val visibleMedia = if (showAllMedia) post.media else post.media.take(1)
            visibleMedia.forEachIndexed { index, media ->
                Box(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp).clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
                    AsyncImage(feedImageRequest(LocalContext.current, media), "Photo ${index + 1} from ${post.author.name}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(320.dp).background(LinkCanvas))
                    if (!showAllMedia && post.media.size > 1) Box(Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = .7f)).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("+${post.media.size - 1}", color = Color.White) }
                }
                if (showAllMedia && index < visibleMedia.lastIndex) Spacer(Modifier.height(4.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("♥ ${post.likeCount}", color = LinkMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("${post.commentCount} comments", color = LinkMuted, fontSize = 12.sp)
        }
        HorizontalDivider(color = LinkDivider)
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            PostAction(if (post.liked) "♥ Liked" else "♡ Like", Modifier.weight(1f), post.liked, enabled, onLike)
            PostAction("□ Comment", Modifier.weight(1f), enabled = enabled, onClick = onOpen)
            PostAction("↗ Share", Modifier.weight(1f), enabled = enabled, onClick = onShare)
        }
    }
}

@Composable private fun PostAction(label: String, modifier: Modifier, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit = {}) {
    Box(modifier.fillMaxSize().clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) LinkPurple else LinkMuted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
    }
}

@Composable
fun CreatePostScreen(me: UserResponse?, repository: PostRepository, onBack: () -> Unit, onPublished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var content by rememberSaveable { mutableStateOf("") }
    val images = remember { mutableStateListOf<File>() }
    var preparing by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    val requestId = remember { UUID.randomUUID().toString() }
    val latestImages by rememberUpdatedState(images.toList())
    DisposableEffect(Unit) { onDispose { latestImages.forEach(File::delete) } }
    BackHandler(enabled = preparing || publishing) { }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)) { uris ->
        if (uris.isNotEmpty()) {
            preparing = true; error = null
            scope.launch {
                try {
                    val selected = preparePostImages(context, uris.take(4))
                    images.forEach(File::delete); images.clear(); images.addAll(selected)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "Cannot read selected photos." }
                finally { preparing = false }
            }
        }
    }
    fun choosePhotos() { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    fun publish() {
        if (publishing || preparing || (content.isBlank() && images.isEmpty())) return
        publishing = true; error = null; progress = 0f
        scope.launch {
            try { repository.create(requestId, content.trim(), images.toList()) { progress = it }; onPublished() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Cannot publish post." }
            finally { publishing = false }
        }
    }
    Column(Modifier.fillMaxSize().background(Color.White).imePadding()) {
        ScreenHeader("Create Post", { if (!preparing && !publishing) onBack() }, action = "Post", onAction = ::publish)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(null, me?.let(::userInitials) ?: "?", 42)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(me?.fullName ?: me?.username ?: "—", fontWeight = FontWeight.Bold)
                        Text("Public", color = LinkMuted, fontSize = 12.sp)
                    }
                }
                LinkUpField(content, { if (it.length <= 5000) content = it }, "What's on your mind?", Modifier.padding(horizontal = 16.dp), singleLine = false)
                Text("${content.length}/5000", color = LinkMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            if (images.isNotEmpty()) item {
                LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(images, key = { it.absolutePath }) { file ->
                        Box {
                            AsyncImage(file, "Selected photo", contentScale = ContentScale.Crop, modifier = Modifier.size(180.dp).clip(RoundedCornerShape(14.dp)))
                            Text("×", color = Color.White, fontSize = 20.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = .65f)).clickable { images.remove(file); file.delete() }.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
            }
            item {
                TextButton(onClick = ::choosePhotos, enabled = !preparing && !publishing) { Text(if (images.isEmpty()) "＋ Add photos (up to 4)" else "Replace photos") }
                if (preparing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            }
        }
        if (publishing) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(if (progress >= 1f) "Publishing post…" else "Uploading ${(progress * 100).toInt()}%", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        PrimaryButton(if (publishing) "Publishing…" else "Publish Post", ::publish, Modifier.padding(16.dp), enabled = me != null && !publishing && !preparing && (content.isNotBlank() || images.isNotEmpty()))
    }
}

@Composable
fun PostDetailScreen(postId: String?, me: UserResponse?, repository: PostRepository, onBack: () -> Unit, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var post by remember(postId) { mutableStateOf<FeedPost?>(null) }
    val comments = remember(postId) { mutableStateListOf<FeedComment>() }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var requestId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDeletePost by remember { mutableStateOf(false) }
    var deletingComment by remember { mutableStateOf<FeedComment?>(null) }
    val list = rememberLazyListState()
    suspend fun load() {
        val id = postId ?: return
        loading = true; error = null
        try {
            post = repository.get(id)
            val page = repository.comments(id)
            comments.clear(); comments.addAll(page.items); cursor = page.nextCursor
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Cannot load post." }
        finally { loading = false }
    }
    LaunchedEffect(postId) { load() }
    val ime = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
    LaunchedEffect(ime) { if (ime > 0 && comments.isNotEmpty()) list.animateScrollToItem(comments.size + 1) }
    Column(Modifier.fillMaxSize().background(LinkCanvas).imePadding()) {
        ScreenHeader("Post", onBack)
        if (loading && post == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(state = list, modifier = Modifier.weight(1f)) {
            post?.let { current -> item {
                PostCard(current, {}, onLike = { scope.launch {
                    try { post = repository.like(current.id, !current.liked) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message }
                } }, onShare = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, current.content) }, "Share post"))
                }, showAllMedia = true, onDelete = if (current.author.id == me?.id) {{ confirmDeletePost = true }} else null)
            } }
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Comments", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                }
            }
            items(comments, key = { it.id }) { comment ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
                    UserAvatar(comment.author.avatarUrl, comment.author.initials, 34)
                    Column(Modifier.weight(1f).padding(start = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp)) {
                        Text(comment.author.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(comment.content)
                        Text(relativePostTime(comment.createdAt), color = LinkMuted, fontSize = 10.sp)
                    }
                    if (comment.author.id == me?.id) TextButton(onClick = { deletingComment = comment }) { Text("Delete", fontSize = 11.sp) }
                }
            }
            if (cursor != null) item { TextButton(onClick = { scope.launch {
                try {
                    val page = repository.comments(postId!!, cursor)
                    comments.addAll(page.items.filter { row -> comments.none { it.id == row.id } }); cursor = page.nextCursor
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message }
            } }) { Text("Load older comments") } }
            if (!loading && comments.isEmpty()) item { Text("Be the first to comment.", color = LinkMuted, modifier = Modifier.padding(16.dp)) }
        }
        if (post != null && me != null) Row(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            LinkUpField(text, { if (it.length <= 1000) { text = it; requestId = UUID.randomUUID().toString() } }, "Write a comment…", Modifier.weight(1f))
            Text(if (sending) " … " else " Send ", color = LinkPurple, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !sending && text.isNotBlank()) {
                val id = postId ?: return@clickable
                sending = true; error = null
                scope.launch {
                    try {
                        val result = repository.comment(id, AddFeedComment(requestId, text.trim()))
                        comments.add(0, result); text = ""; requestId = UUID.randomUUID().toString(); post = repository.get(id); list.animateScrollToItem(2)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message }
                    finally { sending = false }
                }
            }.padding(8.dp))
        }
    }
    if (confirmDeletePost) AlertDialog(onDismissRequest = { confirmDeletePost = false }, title = { Text("Delete this post?") }, text = { Text("Its photos, likes and comments will be removed.") },
        confirmButton = { TextButton(onClick = { confirmDeletePost = false; scope.launch {
            try { repository.delete(postId!!); onDeleted() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
        } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDeletePost = false }) { Text("Cancel") } })
    deletingComment?.let { comment -> AlertDialog(onDismissRequest = { deletingComment = null }, title = { Text("Delete comment?") },
        confirmButton = { TextButton(onClick = { deletingComment = null; scope.launch {
            try { repository.deleteComment(postId!!, comment.id); comments.removeAll { it.id == comment.id }; post = repository.get(postId) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
        } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deletingComment = null }) { Text("Cancel") } }) }
}

@Composable private fun UserAvatar(url: String?, initials: String, size: Int) {
    if (url == null) Avatar(initials, size)
    else AsyncImage(ApiClient.mediaUrl(url), "Avatar", contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp).clip(CircleShape))
}

private fun userInitials(user: UserResponse): String = (user.fullName ?: user.username).split(' ').filter(String::isNotBlank).take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

private fun feedImageRequest(context: android.content.Context, media: FeedMedia): ImageRequest = ImageRequest.Builder(context)
    .data(ApiClient.mediaUrl(media.url))
    // The signed Supabase URL changes, but the immutable media id does not.
    .memoryCacheKey("feed-media:${media.id}")
    .diskCacheKey("feed-media:${media.id}")
    .crossfade(false)
    .build()
