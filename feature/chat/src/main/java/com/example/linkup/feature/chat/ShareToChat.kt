package com.example.linkup.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.linkup.core.designsystem.component.Avatar
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.SharedContent
import com.example.linkup.data.network.ApiClient
import com.example.linkup.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

@HiltViewModel
class ShareToChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {
    val conversations: StateFlow<List<Conversation>> = chatRepository.conversationsState

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun refresh() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            chatRepository.refreshConversations()
                .onFailure { _error.value = it.message ?: "Không tải được cuộc trò chuyện" }
            _loading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun share(content: SharedContent, conversationIds: Set<String>, onComplete: (Int) -> Unit) {
        if (_sending.value || conversationIds.isEmpty()) return
        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            val results = supervisorScope {
                conversationIds.map { id -> async { chatRepository.sendSharedContent(id, content) } }.awaitAll()
            }
            val sent = results.count { it.isSuccess }
            _sending.value = false
            if (sent == conversationIds.size) onComplete(sent)
            else _error.value = "Đã gửi $sent/${conversationIds.size}. Kiểm tra kết nối rồi thử lại."
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareToChatSheet(
    content: SharedContent,
    onDismiss: () -> Unit,
    onShared: (Int) -> Unit,
    viewModel: ShareToChatViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val sending by viewModel.sending.collectAsState()
    val error by viewModel.error.collectAsState()
    var query by remember(content.id) { mutableStateOf("") }
    var selected by remember(content.id) { mutableStateOf(emptySet<String>()) }
    val visible = remember(conversations, query) {
        conversations.filter { conversation ->
            query.isBlank() || conversation.user.name.contains(query, ignoreCase = true)
        }
    }

    LaunchedEffect(content.id) {
        viewModel.clearError()
        viewModel.refresh()
    }

    ModalBottomSheet(onDismissRequest = { if (!sending) onDismiss() }) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Text(
                if (content.type == SharedContent.TYPE_REEL) "Chia sẻ Reel" else "Chia sẻ bài viết",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 21.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            SharedContentPreview(content)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Tìm cuộc trò chuyện") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            error?.let {
                Text(it, color = Color(0xFFB3261E), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
            }
            when {
                loading && conversations.isEmpty() -> Box(
                    Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = LinkPurple) }

                visible.isEmpty() -> Box(
                    Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center
                ) { Text("Không tìm thấy cuộc trò chuyện", color = LinkMuted) }

                else -> LazyColumn(Modifier.fillMaxWidth().height(280.dp)) {
                    items(visible, key = { it.id }) { conversation ->
                        ShareConversationRow(
                            conversation = conversation,
                            selected = conversation.id in selected,
                            enabled = !sending,
                            onClick = {
                                selected = if (conversation.id in selected) selected - conversation.id
                                else selected + conversation.id
                            },
                        )
                    }
                }
            }
            HorizontalDivider()
            Button(
                onClick = { viewModel.share(content, selected, onShared) },
                enabled = selected.isNotEmpty() && !sending,
                colors = ButtonDefaults.buttonColors(containerColor = LinkPurple),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp),
            ) {
                if (sending) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                else Text(if (selected.size <= 1) "Gửi" else "Gửi đến ${selected.size} cuộc trò chuyện")
            }
        }
    }
}

@Composable
private fun SharedContentPreview(content: SharedContent) {
    val previewUrl = content.previewUrl
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color(0xFFF3F0F8)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (previewUrl != null) {
            AsyncImage(
                model = ApiClient.mediaUrl(previewUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(Modifier.weight(1f).padding(start = if (previewUrl != null) 10.dp else 0.dp)) {
            Text(if (content.type == SharedContent.TYPE_REEL) "Reel" else "Bài viết", fontWeight = FontWeight.Bold)
            Text(content.caption.ifBlank { "Nội dung được chia sẻ" }, maxLines = 2, overflow = TextOverflow.Ellipsis, color = LinkMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ShareConversationRow(
    conversation: Conversation,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val avatarUrl = conversation.user.avatarUrl
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                ApiClient.mediaUrl(avatarUrl),
                conversation.user.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(CircleShape),
            )
        } else Avatar(conversation.user.initials, 44)
        Column(Modifier.weight(1f)) {
            Text(conversation.user.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(conversation.preview, color = LinkMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(if (selected) LinkPurple else Color(0xFFE5E1EA)),
            contentAlignment = Alignment.Center,
        ) { if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}
