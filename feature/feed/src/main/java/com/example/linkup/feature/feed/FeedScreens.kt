package com.example.linkup.feature.feed

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.example.linkup.core.designsystem.component.Avatar
import com.example.linkup.core.designsystem.component.LinkUpField
import com.example.linkup.core.designsystem.component.LinkUpTopBar
import com.example.linkup.core.designsystem.icon.LinkUpIcons
import com.example.linkup.core.designsystem.component.MediaPlaceholder
import com.example.linkup.core.designsystem.component.PrimaryButton
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.data.model.ChatMessage
import com.example.linkup.data.model.Post
import com.example.linkup.data.model.User
import com.example.linkup.core.designsystem.theme.LinkCanvas
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPink
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft

@Composable
fun FeedScreen(
    me: User,
    posts: List<Post>,
    onCreatePost: () -> Unit,
    onOpenPost: (Post) -> Unit,
    onLike: (String) -> Unit,
    onProfile: () -> Unit,
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onAi: () -> Unit,
    unreadNotifications: Int = 0,
    onFriends: (() -> Unit)? = null,
    pendingFriendRequests: Int = 0,
    /** Opens a post author's profile. Null leaves author rows inert. */
    onOpenAuthor: ((String) -> Unit)? = null
) {
    Column(Modifier.fillMaxSize().background(LinkCanvas)) {
        LinkUpTopBar(
            onSearch = onSearch,
            onNotifications = onNotifications,
            onAi = onAi,
            unreadNotifications = unreadNotifications,
            onFriends = onFriends,
            pendingFriendRequests = pendingFriendRequests
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().background(Color.White).clickable(onClick = onCreatePost).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.clickable(onClick = onProfile)) { Avatar(me.initials) }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(LinkCanvas).padding(horizontal = 16.dp, vertical = 12.dp)
                    ) { Text("What's on your mind?", color = LinkMuted) }
                    Icon(LinkUpIcons.Plus, "Create post", tint = LinkPurple, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            items(posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onOpen = { onOpenPost(post) },
                    onLike = { onLike(post.id) },
                    onOpenAuthor = onOpenAuthor?.let { open -> { open(post.author.id) } }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onOpenAuthor: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        // Tapping the author opens their profile — the gesture every social app has.
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onOpenAuthor != null) Modifier.clickable(onClick = onOpenAuthor) else Modifier)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(post.author.initials)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(post.author.name, fontWeight = FontWeight.Bold)
                Text("${post.author.username} · ${post.time}", color = LinkMuted, fontSize = 12.sp)
            }
            Icon(LinkUpIcons.MoreHorizontal, "Post options", tint = LinkMuted, modifier = Modifier.size(18.dp))
        }
        Text(post.content, modifier = Modifier.padding(horizontal = 16.dp).clickable(onClick = onOpen), lineHeight = 21.sp)
        post.mediaLabel?.let { mediaLabel ->
            Spacer(Modifier.height(12.dp))
            MediaPlaceholder(mediaLabel, Modifier.clickable(onClick = onOpen))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("♥ ${post.likes}", color = LinkMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("${post.comments} comments", color = LinkMuted, fontSize = 12.sp)
        }
        HorizontalDivider(color = LinkDivider)
        Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
            LikeAction(liked = post.liked, modifier = Modifier.weight(1f), onClick = onLike)
            PostAction("□ Comment", Modifier.weight(1f), onClick = onOpen)
            PostAction("↗ Share", Modifier.weight(1f))
        }
    }
}

@Composable
private fun PostAction(label: String, modifier: Modifier, selected: Boolean = false, onClick: () -> Unit = {}) {
    Box(modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) LinkPurple else LinkMuted, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
    }
}

@Composable
fun CreatePostScreen(me: User, onBack: () -> Unit, onPublish: (String) -> Unit) {
    var content by remember { mutableStateOf("") }
    var hasMedia by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color.White).imePadding()) {
        ScreenHeader("Create Post", onBack, action = "Post", onAction = { if (content.isNotBlank()) onPublish(content) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(me.initials)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(me.name, fontWeight = FontWeight.Bold)
                    Text("Friends ▾", color = LinkMuted, fontSize = 12.sp)
                }
            }
            LinkUpField(
                value = content,
                onValueChange = { content = it },
                label = "What's on your mind?",
                modifier = Modifier.padding(horizontal = 16.dp),
                singleLine = false
            )
            if (hasMedia) {
                MediaPlaceholder("Selected media", Modifier.padding(16.dp).clip(RoundedCornerShape(14.dp)))
                Text("Remove media", color = LinkPurple, modifier = Modifier.align(Alignment.End).clickable { hasMedia = false }.padding(horizontal = 16.dp))
            }
        }
        HorizontalDivider(color = LinkDivider)
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Add to your post", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("▧  Photo/Video", color = LinkPurple, modifier = Modifier.clickable { hasMedia = true })
        }
        PrimaryButton("Publish Post", { onPublish(content) }, Modifier.padding(16.dp), enabled = content.isNotBlank())
    }
}

/**
 * The like control, with a spring that overshoots on the way in.
 *
 * Liking is the most repeated gesture in the app; a small bounce makes it feel
 * acknowledged without slowing anything down.
 */
@Composable
private fun LikeAction(liked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (liked) 1.18f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "likeScale"
    )
    val tint by animateColorAsState(
        targetValue = if (liked) LinkPink else LinkMuted,
        animationSpec = tween(180),
        label = "likeTint"
    )

    Row(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (liked) LinkUpIcons.HeartFilled else LinkUpIcons.Heart,
            contentDescription = if (liked) "Unlike" else "Like",
            tint = tint,
            modifier = Modifier.size(18.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        )
        Spacer(Modifier.width(6.dp))
        Text(if (liked) "Liked" else "Like", color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PostDetailScreen(
    post: Post?,
    onBack: () -> Unit,
    onLike: (String) -> Unit,
    onOpenAuthor: ((String) -> Unit)? = null
) {
    var comment by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf(listOf(ChatMessage("comment1", "This looks fantastic!", false, "1h"))) }
    val commentScrollState = rememberScrollState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom, comments.size, commentScrollState.maxValue) {
        if (imeBottom > 0) commentScrollState.animateScrollTo(commentScrollState.maxValue)
    }
    Column(Modifier.fillMaxSize().background(LinkCanvas).imePadding()) {
        ScreenHeader("Post", onBack)
        if (post != null) {
            Column(Modifier.weight(1f).verticalScroll(commentScrollState)) {
                PostCard(
                    post = post,
                    onOpen = {},
                    onLike = { onLike(post.id) },
                    onOpenAuthor = onOpenAuthor?.let { open -> { open(post.author.id) } }
                )
                Text("Comments", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                comments.forEach { item ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Avatar(if (item.fromMe) "SJ" else "AC", 34)
                        Column(Modifier.padding(start = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp)) {
                            Text(if (item.fromMe) "Sarah Jones" else "Alex Chen", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(item.text)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LinkUpField(comment, { comment = it }, "Write a comment…", Modifier.weight(1f))
                Text(" Send ", color = LinkPurple, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                    if (comment.isNotBlank()) {
                        comments = comments + ChatMessage("comment${comments.size + 1}", comment, true, "Now")
                        comment = ""
                    }
                }.padding(8.dp))
            }
        }
    }
}
