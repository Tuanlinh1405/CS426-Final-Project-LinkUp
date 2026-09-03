package com.example.linkup.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.core.designsystem.component.LinkUpField
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.core.designsystem.icon.LinkUpIcons
import com.example.linkup.core.designsystem.theme.*
import com.example.linkup.data.ai.AiConversation
import com.example.linkup.data.ai.AiMessage
import com.example.linkup.data.ai.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AiViewModel @Inject constructor(private val repository: AiRepository) : ViewModel() {
    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()
    private val _conversations = MutableStateFlow<List<AiConversation>>(emptyList())
    val conversations: StateFlow<List<AiConversation>> = _conversations.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var conversationId: String? = null
    private var openJob: Job? = null
    private var sendJob: Job? = null
    private var historyJob: Job? = null
    private var openKey: String? = null

    fun open(id: String?, postId: String?, onConversationReady: (String) -> Unit) {
        val key = "${id.orEmpty()}:${postId.orEmpty()}"
        if (openKey == key) return
        openKey = key
        openJob?.cancel()
        openJob = viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when {
                postId != null -> repository.analyzePost(postId).fold(
                    onSuccess = { result ->
                        conversationId = result.conversation.id
                        _messages.value = result.messages
                        // The parent swaps the temporary post-analysis route state for the
                        // persisted conversation id. Mark that destination as already open so
                        // recomposition does not immediately reload the same conversation.
                        openKey = "${result.conversation.id}:"
                        onConversationReady(result.conversation.id)
                    },
                    onFailure = {
                        if (openKey == key) openKey = null
                        _error.value = it.message ?: "Không phân tích được bài viết."
                    },
                )
                id != null -> repository.messages(id).fold(
                    onSuccess = { conversationId = id; _messages.value = it },
                    onFailure = {
                        if (openKey == key) openKey = null
                        _error.value = it.message ?: "Không tải được cuộc trò chuyện."
                    },
                )
                else -> { conversationId = null; _messages.value = emptyList() }
            }
            val pendingConversation = conversationId
            if (
                pendingConversation != null &&
                _messages.value.isNotEmpty() &&
                _messages.value.last().role != "model"
            ) {
                waitForAnalysis(pendingConversation)
            }
            _loading.value = false
        }
    }

    private suspend fun waitForAnalysis(id: String) {
        val deadline = System.nanoTime() + 120_000_000_000L
        var consecutiveFailures = 0
        var pollDelayMs = 1_000L
        while (currentCoroutineContext().isActive && conversationId == id && System.nanoTime() < deadline) {
            delay(pollDelayMs)
            pollDelayMs = (pollDelayMs + 500L).coerceAtMost(3_000L)
            repository.messages(id).fold(
                onSuccess = { latest ->
                    consecutiveFailures = 0
                    _messages.value = latest
                    if (latest.lastOrNull()?.role == "model") return
                },
                onFailure = {
                    consecutiveFailures++
                    if (consecutiveFailures >= 3) {
                        _error.value = it.message ?: "Mất kết nối khi chờ kết quả AI."
                        return
                    }
                },
            )
        }
        if (conversationId == id && _messages.value.lastOrNull()?.role != "model") {
            _error.value = "AI vẫn đang xử lý. Bạn có thể quay lại cuộc trò chuyện này trong lịch sử."
        }
    }

    fun send(prompt: String, onConversationReady: (String) -> Unit) {
        val clean = prompt.trim()
        if (clean.isEmpty() || _sending.value || _loading.value) return
        sendJob = viewModelScope.launch {
            _sending.value = true
            _error.value = null
            val base = _messages.value
            val temporary = AiMessage(UUID.randomUUID().toString(), conversationId.orEmpty(), "user", clean, "")
            _messages.value = base + temporary
            var target = conversationId
            if (target == null) {
                repository.createConversation(clean.take(70)).fold(
                    onSuccess = { created ->
                        target = created.id
                        conversationId = created.id
                        openKey = "${created.id}:"
                        onConversationReady(created.id)
                    },
                    onFailure = { _error.value = it.message ?: "Không tạo được cuộc trò chuyện." },
                )
            }
            val resolved = target
            if (resolved != null) {
                repository.send(resolved, clean).fold(
                    onSuccess = { exchange -> _messages.value = base + exchange },
                    onFailure = { _messages.value = base; _error.value = it.message ?: "Không gửi được câu hỏi." },
                )
            } else _messages.value = base
            _sending.value = false
        }
    }

    fun refreshHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            _loading.value = true
            repository.conversations().fold(
                onSuccess = { _conversations.value = it },
                onFailure = { _error.value = it.message ?: "Không tải được lịch sử." },
            )
            _loading.value = false
        }
    }

    fun clearError() { _error.value = null }

    fun reset() {
        openJob?.cancel()
        sendJob?.cancel()
        historyJob?.cancel()
        openJob = null
        sendJob = null
        historyJob = null
        openKey = null
        conversationId = null
        _messages.value = emptyList()
        _conversations.value = emptyList()
        _loading.value = false
        _sending.value = false
        _error.value = null
    }
}

@Composable
fun AiChatScreen(
    onBack: () -> Unit,
    onHistory: () -> Unit,
    conversationId: String? = null,
    analyzePostId: String? = null,
    onConversationReady: (String) -> Unit = {},
    viewModel: AiViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val sending by viewModel.sending.collectAsState()
    val error by viewModel.error.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(conversationId, analyzePostId) { viewModel.open(conversationId, analyzePostId, onConversationReady) }
    LaunchedEffect(messages.size, loading, sending, imeBottom) {
        val extra = if (loading || sending) 1 else 0
        if (messages.isNotEmpty() || extra > 0) listState.animateScrollToItem((messages.size + extra - 1).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize().background(LinkCanvas).imePadding()) {
        ScreenHeader("LinkUp AI", onBack, action = "Lịch sử", onAction = onHistory)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty() && !loading) item("welcome") {
                AiBubble("Gửi bài viết cho mình phân tích, hoặc hỏi bất cứ điều gì nhé.", false)
            }
            items(messages, key = { it.id }) { message -> AiBubble(message.content, message.role == "user") }
            if (loading || sending) item("ai-thinking") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AiAvatar()
                    Row(Modifier.padding(start = 8.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LinkPurple)
                        Text(if (loading) "Đang xem và phân tích bài viết…" else "Đang suy nghĩ…", color = LinkMuted, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
        error?.let { message ->
            Row(Modifier.fillMaxWidth().background(Color(0xFFFFEDEA)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, color = Color(0xFFB3261E), fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::clearError) { Text("Đóng") }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            LinkUpField(draft, { if (it.length <= 2_000) draft = it }, "Hỏi LinkUp AI…", Modifier.weight(1f))
            Icon(LinkUpIcons.Send, "Gửi", tint = if (draft.isNotBlank() && !loading && !sending) LinkPurple else LinkMuted,
                modifier = Modifier.size(32.dp).clickable(enabled = draft.isNotBlank() && !loading && !sending) {
                    val prompt = draft; draft = ""; viewModel.send(prompt, onConversationReady)
                }.padding(start = 10.dp))
        }
    }
}

@Composable private fun AiBubble(text: String, fromMe: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!fromMe) AiAvatar()
        Text(text, color = if (fromMe) Color.White else Color(0xFF1E1B2E), lineHeight = 21.sp,
            modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(.82f).clip(RoundedCornerShape(16.dp)).background(if (fromMe) LinkPurple else Color.White).padding(14.dp))
    }
}

@Composable private fun AiAvatar() {
    Box(Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(LinkPurple, Color(0xFFFF4D98)))), contentAlignment = Alignment.Center) {
        Text("AI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun AiConversationsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, viewModel: AiViewModel = hiltViewModel()) {
    val conversations by viewModel.conversations.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshHistory() }
    Column(Modifier.fillMaxSize().background(LinkCanvas)) {
        ScreenHeader("Lịch sử LinkUp AI", onBack)
        when {
            loading && conversations.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LinkPurple) }
            conversations.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error ?: "Chưa có cuộc trò chuyện nào", color = LinkMuted) }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(conversations, key = { it.id }) { conversation ->
                    Row(Modifier.fillMaxWidth().background(Color.White).clickable { onOpen(conversation.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(CircleShape).background(LinkPurpleSoft).padding(12.dp)) { Text("AI", color = LinkPurple, fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(conversation.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(conversation.lastMessage ?: "Nhấn để tiếp tục", color = LinkMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(1.dp))
                }
            }
        }
    }
}
