package com.example.linkup.feature.reels.comments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.linkup.core.designsystem.component.Avatar
import com.example.linkup.data.reels.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCommentsSheet(reel: Reel, me: String, repository: ReelRepository, onDismiss: () -> Unit, onChanged: (Long) -> Unit) {
    val comments = remember(reel.id) { mutableStateListOf<ReelComment>() }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var requestId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<ReelComment?>(null) }
    var replyingTo by remember { mutableStateOf<ReelComment?>(null) }
    val liking = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
    fun updateComment(updated: ReelComment) {
        val rootIndex = comments.indexOfFirst { it.id == updated.id || it.replies.any { reply -> reply.id == updated.id } }
        if (rootIndex < 0) return
        val root = comments[rootIndex]
        comments[rootIndex] = if (root.id == updated.id) updated.copy(replies = root.replies)
        else root.copy(replies = root.replies.map { if (it.id == updated.id) updated else it })
    }
    fun toggleCommentLike(comment: ReelComment) {
        if (liking[comment.id] == true) return
        val target = !comment.liked
        val optimistic = comment.copy(liked = target, likeCount = (comment.likeCount + if (target) 1 else -1).coerceAtLeast(0))
        updateComment(optimistic); liking[comment.id] = true
        scope.launch {
            try { updateComment(repository.likeComment(reel.id, comment.id, target)) }
            catch (e: CancellationException) { updateComment(comment); throw e }
            catch (e: Exception) { updateComment(comment); error = e.message }
            finally { liking.remove(comment.id) }
        }
    }
    suspend fun load(more: Boolean) {
        if (loading) return
        loading = true; error = null
        try { val page = repository.comments(reel.id, if (more) cursor else null); if (!more) comments.clear(); comments.addAll(page.items.filter { row -> comments.none { it.id == row.id } }); cursor = page.nextCursor }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
        finally { loading = false }
    }
    LaunchedEffect(reel.id) { load(false) }
    LaunchedEffect(ime) { if (ime > 0 && comments.isNotEmpty()) list.animateScrollToItem(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Text("Comments", style = MaterialTheme.typography.titleLarge)
            if (error != null) Row { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f)); TextButton(onClick = { scope.launch { load(false) } }) { Text("Retry") } }
            if (loading && comments.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(state = list, reverseLayout = true, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
                if (!loading && comments.isEmpty()) item { Text("Be the first to comment.", modifier = Modifier.padding(vertical = 16.dp)) }
                items(comments, key = { it.id }) { comment ->
                    ReelCommentThread(comment, me, liking, onReply = { replyingTo = it }, onDelete = { deleting = it }, onLike = ::toggleCommentLike)
                }
                if (cursor != null) item { TextButton(onClick = { scope.launch { load(true) } }, enabled = !loading) { Text(if (loading) "Loading…" else "Load older comments") } }
            }
            replyingTo?.let { target -> Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Replying to ${target.author.name}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { replyingTo = null }) { Text("Cancel") }
            } }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                OutlinedTextField(value = text, onValueChange = { if (it.length <= 1000) { text = it; requestId = UUID.randomUUID().toString() } }, enabled = !sending,
                    placeholder = { Text(if (replyingTo == null) "Add a comment…" else "Add a reply…") }, modifier = Modifier.weight(1f), maxLines = 4)
                TextButton(enabled = !sending && text.isNotBlank(), onClick = {
                    sending = true; error = null
                    scope.launch {
                        try {
                            val parentId = replyingTo?.let { it.parentId ?: it.id }
                            val result = repository.comment(reel.id, AddComment(requestId, text.trim(), parentId))
                            if (parentId == null) {
                                if (comments.none { it.id == result.id }) comments.add(0, result)
                            } else {
                                val index = comments.indexOfFirst { it.id == parentId }
                                if (index >= 0 && comments[index].replies.none { it.id == result.id }) {
                                    comments[index] = comments[index].copy(replies = comments[index].replies + result)
                                }
                            }
                            text = ""; replyingTo = null; requestId = UUID.randomUUID().toString(); list.animateScrollToItem(0); onChanged(1)
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message }
                        finally { sending = false }
                    }
                }) { Text(if (sending) "…" else "Send") }
            }
        }
    }
    deleting?.let { comment -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete comment?") },
        confirmButton = { TextButton(onClick = { deleting = null; scope.launch {
            try {
                repository.deleteComment(reel.id, comment.id)
                val rootIndex = comments.indexOfFirst { it.id == comment.id || it.replies.any { reply -> reply.id == comment.id } }
                if (rootIndex >= 0) {
                    if (comments[rootIndex].id == comment.id) comments.removeAt(rootIndex)
                    else comments[rootIndex] = comments[rootIndex].copy(replies = comments[rootIndex].replies.filterNot { it.id == comment.id })
                }
                if (replyingTo?.id == comment.id) replyingTo = null
                onChanged(-1)
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
        } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}

@Composable private fun ReelCommentThread(
    root: ReelComment,
    me: String,
    liking: Map<String, Boolean>,
    onReply: (ReelComment) -> Unit,
    onDelete: (ReelComment) -> Unit,
    onLike: (ReelComment) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        ReelCommentRow(root, me, nested = false, liking[root.id] != true, onReply, onDelete, onLike)
        root.replies.forEach { reply -> ReelCommentRow(reply, me, nested = true, liking[reply.id] != true, onReply, onDelete, onLike) }
    }
}

@Composable private fun ReelCommentRow(
    comment: ReelComment,
    me: String,
    nested: Boolean,
    likeEnabled: Boolean,
    onReply: (ReelComment) -> Unit,
    onDelete: (ReelComment) -> Unit,
    onLike: (ReelComment) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = if (nested) 42.dp else 0.dp, top = 5.dp, bottom = 5.dp)) {
        Avatar(comment.author.initials, if (nested) 27 else 32)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(comment.author.name, style = MaterialTheme.typography.labelLarge)
            Text(comment.content)
            Row {
                TextButton(onClick = { onLike(comment) }, enabled = likeEnabled) {
                    Text("${if (comment.liked) "♥" else "♡"} ${comment.likeCount}", color = if (comment.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onReply(comment) }) { Text("Reply") }
                if (comment.author.id == me) TextButton(onClick = { onDelete(comment) }) { Text("Delete") }
            }
        }
    }
}
