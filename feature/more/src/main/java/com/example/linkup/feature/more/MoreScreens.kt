package com.example.linkup.feature.more

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.ui.Avatar
import com.example.linkup.core.ui.ChoiceChip
import com.example.linkup.core.ui.EmptyState
import com.example.linkup.core.ui.LinkUpField
import com.example.linkup.core.ui.ScreenHeader
import com.example.linkup.data.model.NotificationItem
import com.example.linkup.data.network.ApiClient
import com.example.linkup.data.search.SearchPerson
import com.example.linkup.data.search.SearchPost
import com.example.linkup.data.search.SearchReel
import com.example.linkup.data.search.SearchRepository
import com.example.linkup.data.search.SearchResults
import com.example.linkup.ui.theme.LinkCanvas
import com.example.linkup.ui.theme.LinkDivider
import com.example.linkup.ui.theme.LinkMuted
import com.example.linkup.ui.theme.LinkPurple
import com.example.linkup.ui.theme.LinkPurpleSoft
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    repository: SearchRepository,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenReel: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("all") }
    var results by remember { mutableStateOf(SearchResults()) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedPerson by remember { mutableStateOf<SearchPerson?>(null) }
    suspend fun runSearch(cursor: String? = null) {
        val clean = query.trim()
        if (clean.length < 2) { results = SearchResults(); error = null; return }
        if (cursor == null) loading = true else loadingMore = true
        error = null
        try {
            val page = repository.search(clean, tab, cursor)
            results = if (cursor == null) page else results.copy(
                people = results.people + page.people.filter { next -> results.people.none { it.id == next.id } },
                posts = results.posts + page.posts.filter { next -> results.posts.none { it.id == next.id } },
                reels = results.reels + page.reels.filter { next -> results.reels.none { it.id == next.id } },
                nextCursor = page.nextCursor,
            )
        } catch (failure: CancellationException) { throw failure }
        catch (failure: Exception) { error = failure.message ?: "Search is unavailable." }
        finally { loading = false; loadingMore = false }
    }
    LaunchedEffect(query, tab) {
        results = SearchResults(); error = null
        if (query.trim().length >= 2) { delay(350); runSearch() }
    }

    Column(Modifier.fillMaxSize().background(LinkCanvas).imePadding()) {
        ScreenHeader("Search", onBack)
        LinkUpField(query, { if (it.length <= 100) query = it }, "Search posts, Reels and people", Modifier.padding(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("all" to "Top", "people" to "People", "posts" to "Posts", "reels" to "Reels").forEach { (value, label) ->
                ChoiceChip(label, selected = tab == value) { tab = value }
            }
        }
        when {
            query.trim().length < 2 -> EmptyState("Search LinkUp", "Enter at least 2 characters to find posts, Reels and people")
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null && results.people.isEmpty() && results.posts.isEmpty() && results.reels.isEmpty() -> EmptyState("Search failed", error.orEmpty())
            results.people.isEmpty() && results.posts.isEmpty() && results.reels.isEmpty() -> EmptyState("No results", "Try another name or keyword")
            else -> LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp)) {
                if (results.people.isNotEmpty()) {
                    item { SearchSection("People", if (tab == "all") "${results.people.size} top results" else null) }
                    items(results.people, key = { "person:${it.id}" }) { PersonResult(it) { selectedPerson = it } }
                }
                if (results.posts.isNotEmpty()) {
                    item { SearchSection("Posts", if (tab == "all") "Matching text and creator" else null) }
                    items(results.posts, key = { "post:${it.id}" }) { PostResult(it) { onOpenPost(it.id) } }
                }
                if (results.reels.isNotEmpty()) {
                    item { SearchSection("Reels", if (tab == "all") "Matching captions and creator" else null) }
                    items(results.reels, key = { "reel:${it.id}" }) { ReelResult(it) { onOpenReel(it.id) } }
                }
                results.nextCursor?.let { cursor -> item {
                    TextButton(onClick = { if (!loadingMore) scope.launch { runSearch(cursor) } }, enabled = !loadingMore, modifier = Modifier.fillMaxWidth()) {
                        Text(if (loadingMore) "Loading…" else "See more results")
                    }
                } }
                error?.let { message -> item { Text(message, color = Color(0xFFE23C5B), modifier = Modifier.padding(16.dp)) } }
            }
        }
    }
    selectedPerson?.let { person -> AlertDialog(
        onDismissRequest = { selectedPerson = null },
        title = { Text(person.name) },
        text = { Column {
            Text("@${person.username}", color = LinkMuted)
            Text("${person.followerCount} followers", modifier = Modifier.padding(top = 8.dp))
            person.bio?.takeIf(String::isNotBlank)?.let { Text(it, modifier = Modifier.padding(top = 10.dp)) }
        } },
        confirmButton = { TextButton(onClick = { selectedPerson = null }) { Text("Close") } },
    ) }
}

@Composable private fun SearchSection(title: String, subtitle: String?) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        subtitle?.let { Text(it, color = LinkMuted, fontSize = 12.sp) }
    }
}

@Composable private fun PersonResult(person: SearchPerson, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        SearchAvatar(person.avatarUrl, person.initials, 48)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(person.name, fontWeight = FontWeight.Bold)
            Text("@${person.username} · ${person.followerCount} followers", color = LinkMuted, fontSize = 12.sp)
            person.bio?.takeIf(String::isNotBlank)?.let { Text(it, maxLines = 1, fontSize = 12.sp) }
        }
    }
    HorizontalDivider(color = LinkDivider)
}

@Composable private fun PostResult(post: SearchPost, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().background(Color.White).clickable(onClick = onClick).padding(14.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchAvatar(post.author.avatarUrl, post.author.initials, 34)
                Column(Modifier.padding(start = 9.dp)) { Text(post.author.name, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text("@${post.author.username}", color = LinkMuted, fontSize = 11.sp) }
            }
            Text(post.content.ifBlank { "Photo post" }, maxLines = 3, modifier = Modifier.padding(top = 9.dp))
            Text("${post.likeCount} likes · ${post.commentCount} comments", color = LinkMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
        }
        post.imageUrl?.let { url -> AsyncImage(
            model = ImageRequest.Builder(context).data(ApiClient.mediaUrl(url)).memoryCacheKey("search-post:${post.imageId ?: post.id}").diskCacheKey("search-post:${post.imageId ?: post.id}").crossfade(false).build(),
            contentDescription = "Post photo", contentScale = ContentScale.Crop,
            modifier = Modifier.padding(start = 10.dp).size(92.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
        ) }
    }
    HorizontalDivider(color = LinkDivider)
}

@Composable private fun ReelResult(reel: SearchReel, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().background(Color.White).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 86.dp, height = 122.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
            reel.thumbnailUrl?.let { AsyncImage(
                ImageRequest.Builder(context).data(ApiClient.mediaUrl(it)).memoryCacheKey("search-reel:${reel.id}").diskCacheKey("search-reel:${reel.id}").crossfade(false).build(),
                "Reel cover", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            ) }
            Text("▶", color = Color.White, fontSize = 24.sp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(reel.caption.ifBlank { "Reel by ${reel.author.name}" }, maxLines = 3, fontWeight = FontWeight.SemiBold)
            Text("@${reel.author.username}", color = LinkMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
            Text("${reel.likeCount} likes · ${reel.commentCount} comments", color = LinkMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
        }
    }
    HorizontalDivider(color = LinkDivider)
}

@Composable private fun SearchAvatar(url: String?, initials: String, size: Int) {
    if (url == null) Avatar(initials, size)
    else AsyncImage(
        ApiClient.mediaUrl(url), "Avatar", contentScale = ContentScale.Crop,
        modifier = Modifier.size(size.dp).clip(CircleShape),
    )
}

@Composable
fun NotificationsScreen(notifications: List<NotificationItem>, onBack: () -> Unit, onOpen: () -> Unit) {
    var unreadOnly by remember { mutableStateOf(false) }
    val visible = if (unreadOnly) notifications.filter { it.unread } else notifications
    Column(Modifier.fillMaxSize().background(Color.White)) {
        ScreenHeader("Notifications", onBack, action = "Mark all read")
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("All", !unreadOnly) { unreadOnly = false }
            ChoiceChip("Unread", unreadOnly) { unreadOnly = true }
        }
        LazyColumn {
            items(visible, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().background(if (item.unread) LinkPurpleSoft.copy(alpha = .45f) else Color.White).clickable(onClick = onOpen).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(item.actor.initials, 44)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(item.actor.name, fontWeight = FontWeight.Bold)
                        Text(item.text, fontSize = 13.sp)
                    }
                    Text(item.time, color = LinkMuted, fontSize = 11.sp)
                }
                HorizontalDivider(color = LinkDivider)
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit, onDatingProfile: () -> Unit) {
    var darkMode by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().background(LinkCanvas)) {
        ScreenHeader("Settings", onBack)
        Column(Modifier.background(Color.White)) {
            SettingRow("Account", "Profile, password and security")
            SettingRow("Dating profile", "Photos, interests and preferences", onDatingProfile)
            ToggleRow("Notifications", "Push and in-app alerts", notifications) { notifications = it }
            ToggleRow("Dark mode", "Use a darker appearance", darkMode) { darkMode = it }
            SettingRow("Privacy", "Blocked users and visibility")
            SettingRow("Server (debug)", "10.0.2.2:8080")
            SettingRow("About LinkUp", "Version 1.0.0")
            Row(Modifier.fillMaxWidth().clickable(onClick = onLogout).padding(18.dp)) { Text("Log out", color = Color(0xFFE23C5B), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = LinkMuted, fontSize = 12.sp) }
        Text("›", color = LinkMuted, fontSize = 24.sp)
    }
    HorizontalDivider(color = LinkDivider)
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = LinkMuted, fontSize = 12.sp) }
        Switch(checked, onChecked)
    }
    HorizontalDivider(color = LinkDivider)
}
