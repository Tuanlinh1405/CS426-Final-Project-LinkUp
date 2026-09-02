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
import com.example.linkup.core.ui.Avatar
import com.example.linkup.data.reels.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCommentsSheet(reel: Reel, me: String, repository: ReelRepository, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val comments = remember(reel.id) { mutableStateListOf<ReelComment>() }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var requestId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<ReelComment?>(null) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
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
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Avatar(comment.author.initials, 32)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(comment.author.name, style = MaterialTheme.typography.labelLarge); Text(comment.content) }
                        if (comment.author.id == me) TextButton(onClick = { deleting = comment }) { Text("Delete") }
                    }
                }
                if (cursor != null) item { TextButton(onClick = { scope.launch { load(true) } }, enabled = !loading) { Text(if (loading) "Loading…" else "Load older comments") } }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                OutlinedTextField(value = text, onValueChange = { if (it.length <= 1000) { text = it; requestId = UUID.randomUUID().toString() } }, enabled = !sending,
                    placeholder = { Text("Add a comment…") }, modifier = Modifier.weight(1f), maxLines = 4)
                TextButton(enabled = !sending && text.isNotBlank(), onClick = {
                    sending = true; error = null
                    scope.launch {
                        try {
                            val result = repository.comment(reel.id, AddComment(requestId, text.trim()))
                            if (comments.none { it.id == result.id }) comments.add(0, result)
                            text = ""; requestId = UUID.randomUUID().toString(); list.animateScrollToItem(0); onChanged()
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
            try { repository.deleteComment(reel.id, comment.id); comments.removeAll { it.id == comment.id }; onChanged() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
        } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}
