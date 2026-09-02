package com.example.linkup.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.linkup.core.designsystem.component.Avatar
import com.example.linkup.core.designsystem.component.LinkUpField
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.core.designsystem.theme.LinkCanvas
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.data.model.ChatMessage
import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.Message
import com.example.linkup.data.model.MessageStatus

@Composable
fun ChatListRoute(
    onOpenChat: (Conversation) -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    ChatListScreen(
        conversations = conversations,
        searchQuery = searchQuery,
        isLoading = isLoading,
        onSearchQueryChange = { viewModel.searchQuery.value = it },
        onCreateChat = { targetUserId ->
            viewModel.createDirectConversation(targetUserId) { newConv ->
                onOpenChat(newConv)
            }
        },
        onOpenChat = onOpenChat
    )
}

@Composable
fun ChatDetailRoute(
    conversationId: String,
    title: String = "Chat",
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(conversationId) {
        viewModel.setConversation(conversationId)
    }

    val domainMessages by viewModel.messages.collectAsState()
    val isPeerTyping by viewModel.isPeerTyping.collectAsState()

    ChatDetailScreen(
        title = title,
        messages = domainMessages,
        isPeerTyping = isPeerTyping,
        onBack = onBack,
        onSend = { text -> viewModel.sendMessage(text) },
        onTyping = { isTyping -> viewModel.sendTyping(isTyping) }
    )
}

@Composable
fun ChatListScreen(
    conversations: List<Conversation>,
    searchQuery: String = "",
    isLoading: Boolean = false,
    onSearchQueryChange: ((String) -> Unit)? = null,
    onCreateChat: ((String) -> Unit)? = null,
    onOpenChat: (Conversation) -> Unit,
) {
    var localQuery by remember { mutableStateOf("") }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var targetUserIdInput by remember { mutableStateOf("") }

    val currentQuery = onSearchQueryChange?.let { searchQuery } ?: localQuery
    val filtered = conversations.filter {
        it.user.name.contains(currentQuery, ignoreCase = true) ||
                it.preview.contains(currentQuery, ignoreCase = true)
    }

    if (showNewChatDialog) {
        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = { Text("Tạo cuộc trò chuyện mới", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Nhập Target User ID của đối phương:", fontSize = 14.sp, color = LinkMuted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetUserIdInput,
                        onValueChange = { targetUserIdInput = it },
                        placeholder = { Text("Ví dụ: UUID của user") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (targetUserIdInput.isNotBlank()) {
                            val idToCreate = targetUserIdInput.trim()
                            showNewChatDialog = false
                            targetUserIdInput = ""
                            onCreateChat?.invoke(idToCreate)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LinkPurple)
                ) {
                    Text("Tạo Chat", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false }) {
                    Text("Hủy", color = LinkMuted)
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().background(Color.White).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Chats", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text(
                "＋",
                color = LinkPurple,
                fontSize = 28.sp,
                modifier = Modifier.clickable { showNewChatDialog = true }
            )
        }
        LinkUpField(
            value = currentQuery,
            onValueChange = { newValue ->
                if (onSearchQueryChange != null) {
                    onSearchQueryChange(newValue)
                } else {
                    localQuery = newValue
                }
            },
            label = "Search conversations",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (filtered.isEmpty()) {
            if (isLoading) {
                // First load in progress and nothing cached yet — show a neutral placeholder
                // instead of the "no chats" empty state, so it never flashes.
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Đang tải cuộc trò chuyện…", color = LinkMuted)
                }
            } else {
                Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💬", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Chưa có cuộc trò chuyện nào",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1E1B2E)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Bấm dấu ＋ góc trên để nhập User ID và bắt đầu trò chuyện thời gian thực!",
                            fontSize = 13.sp,
                            color = LinkMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showNewChatDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = LinkPurple)
                        ) {
                            Text("Tạo cuộc trò chuyện mới", color = Color.White)
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp)) {
                items(filtered, key = { it.id }) { conversation ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenChat(conversation) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(conversation.user.initials, 48)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(conversation.user.name, fontWeight = FontWeight.Bold)
                            Text(
                                conversation.preview,
                                color = LinkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 13.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(conversation.time, color = LinkMuted, fontSize = 11.sp)
                            if (conversation.unread > 0) {
                                Box(
                                    Modifier.clip(CircleShape).background(LinkPurple).padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(conversation.unread.toString(), color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = LinkDivider, modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }
}

@Composable
fun ChatDetailScreen(
    title: String = "Alex Chen · online",
    messages: List<Message>,
    isPeerTyping: Boolean = false,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onTyping: ((Boolean) -> Unit)? = null,
) {
    var draft by remember { mutableStateOf("") }
    val messageListState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(messages.size, imeBottom) {
        if (messages.isNotEmpty()) {
            messageListState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(draft) {
        onTyping?.invoke(draft.isNotBlank())
    }

    val headerTitle = if (isPeerTyping) "$title (typing...)" else title

    Column(Modifier.fillMaxSize().background(LinkCanvas).imePadding()) {
        ScreenHeader(headerTitle, onBack, action = "•••")
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = messageListState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = false
        ) {
            item {
                Text(
                    "Today",
                    color = LinkMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
            items(messages, key = { it.id }) { message ->
                DomainMessageBubble(message)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("＋", color = LinkPurple, fontSize = 26.sp, modifier = Modifier.padding(end = 6.dp))
            LinkUpField(draft, { draft = it }, "Type a message", Modifier.weight(1f))
            Text(
                "➤",
                color = LinkPurple,
                fontSize = 24.sp,
                modifier = Modifier.clickable {
                    if (draft.isNotBlank()) {
                        onSend(draft)
                        draft = ""
                    }
                }.padding(start = 10.dp)
            )
        }
    }
}

@Composable
fun ChatDetailScreen(
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
) {
    val domainMessages = messages.map { chatMsg ->
        Message(
            id = chatMsg.id,
            conversationId = "",
            senderId = if (chatMsg.fromMe) "me" else "other",
            textContent = chatMsg.text,
            status = MessageStatus.fromString(chatMsg.status),
            createdAt = chatMsg.time,
            fromMe = chatMsg.fromMe
        )
    }
    ChatDetailScreen(
        title = "Alex Chen · online",
        messages = domainMessages,
        isPeerTyping = false,
        onBack = onBack,
        onSend = onSend
    )
}

@Composable
private fun DomainMessageBubble(message: Message) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start
    ) {
        if (!message.fromMe) Avatar("AC", 30)
        Column(
            Modifier.padding(horizontal = 7.dp).widthIn(max = 280.dp).clip(
                RoundedCornerShape(
                    16.dp,
                    16.dp,
                    if (message.fromMe) 3.dp else 16.dp,
                    if (message.fromMe) 16.dp else 3.dp
                )
            ).background(if (message.fromMe) LinkPurple else Color.White).padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                message.textContent ?: "",
                color = if (message.fromMe) Color.White else Color(0xFF1E1B2E)
            )
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val formattedTime = if (message.createdAt.length >= 16 && message.createdAt.contains("T")) {
                    message.createdAt.substringAfter("T").take(5)
                } else message.createdAt.ifEmpty { "Now" }

                Text(
                    formattedTime,
                    color = if (message.fromMe) Color.White.copy(alpha = .7f) else LinkMuted,
                    fontSize = 9.sp
                )
                if (message.fromMe) {
                    MessageStatusIndicator(message.status)
                }
            }
        }
    }
}

@Composable
private fun MessageStatusIndicator(status: MessageStatus) {
    val (iconText, iconColor) = when (status) {
        MessageStatus.SEEN -> "✓✓" to Color(0xFF00BFFF)
        MessageStatus.DELIVERED -> "✓✓" to Color.White.copy(alpha = 0.9f)
        MessageStatus.SENT -> "✓" to Color.White.copy(alpha = 0.7f)
    }
    Text(
        text = "  $iconText",
        color = iconColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
    )
}
