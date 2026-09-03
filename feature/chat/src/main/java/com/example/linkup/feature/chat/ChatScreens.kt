package com.example.linkup.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.linkup.core.designsystem.component.CircleAvatar
import com.example.linkup.core.designsystem.component.GroupAvatar
import com.example.linkup.core.designsystem.component.LinkUpField
import com.example.linkup.core.designsystem.icon.LinkUpIcons
import com.example.linkup.core.designsystem.theme.LinkCanvas
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.data.model.ChatMessage
import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.Message
import com.example.linkup.data.model.MessageStatus
import com.example.linkup.data.model.UserSummary
import com.example.linkup.data.util.ChatTime

@Composable
fun ChatListRoute(
    onOpenChat: (Conversation) -> Unit,
    onOpenProfile: ((String) -> Unit)? = null,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val typingConversationIds by viewModel.typingConversationIds.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val showGroupDialog by viewModel.showGroupDialog.collectAsState()
    val isCreatingGroup by viewModel.isCreatingGroup.collectAsState()
    val banner by viewModel.banner.collectAsState()

    ChatListScreen(
        conversations = conversations,
        searchQuery = searchQuery,
        isLoading = isLoading,
        typingConversationIds = typingConversationIds,
        friends = friends,
        showGroupDialog = showGroupDialog,
        isCreatingGroup = isCreatingGroup,
        banner = banner,
        onBannerDismiss = viewModel::consumeBanner,
        onSearchQueryChange = { viewModel.searchQuery.value = it },
        onCreateChat = { targetUserId ->
            viewModel.createDirectConversation(targetUserId) { newConv ->
                onOpenChat(newConv)
            }
        },
        onOpenGroupDialog = viewModel::openGroupDialog,
        onDismissGroupDialog = viewModel::dismissGroupDialog,
        onCreateGroup = { name, memberIds ->
            viewModel.createGroupConversation(name, memberIds) { newConv -> onOpenChat(newConv) }
        },
        onOpenChat = onOpenChat,
        onOpenProfile = onOpenProfile
    )
}

@Composable
fun ChatDetailRoute(
    conversationId: String,
    title: String = "Chat",
    onBack: () -> Unit,
    peerUserId: String? = null,
    onOpenProfile: ((String) -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(conversationId) {
        viewModel.setConversation(conversationId)
    }

    // The list screen keeps this ViewModel alive, so the repository must be told when the
    // thread stops being visible — otherwise later messages never badge the row again.
    DisposableEffect(conversationId) {
        onDispose { viewModel.onLeaveConversation() }
    }

    val domainMessages by viewModel.messages.collectAsState()
    val isPeerTyping by viewModel.isPeerTyping.collectAsState()
    val currentConversation by viewModel.currentConversation.collectAsState()
    val participantFaces by viewModel.participantFaces.collectAsState()
    val isPeerOnline by viewModel.isPeerOnline.collectAsState()
    val isLoadingOlder by viewModel.isLoadingOlder.collectAsState()

    val conv = currentConversation
    ChatDetailScreen(
        title = conv?.user?.name ?: title,
        messages = domainMessages,
        isPeerTyping = isPeerTyping,
        isPeerOnline = isPeerOnline,
        isLoadingOlder = isLoadingOlder,
        isGroup = conv?.isGroup ?: false,
        avatarUrl = conv?.user?.avatarUrl,
        initials = conv?.user?.initials ?: "C",
        groupFaces = conv?.others.orEmpty().take(2).map { it.avatarUrl to it.initials },
        participantAvatars = participantFaces,
        onBack = onBack,
        onSend = { text -> viewModel.sendMessage(text) },
        onTyping = { isTyping -> viewModel.onDraftChanged(isTyping) },
        onSendImage = { uri -> viewModel.sendImage(uri) },
        onDeleteMessage = { msgId -> viewModel.deleteMessage(msgId) },
        onLoadOlder = { viewModel.loadOlder() },
        onOpenProfile = if (peerUserId != null && onOpenProfile != null) {
            { onOpenProfile(peerUserId) }
        } else {
            null
        }
    )
}

@Composable
fun ChatListScreen(
    conversations: List<Conversation>,
    searchQuery: String = "",
    isLoading: Boolean = false,
    typingConversationIds: Set<String> = emptySet(),
    friends: List<UserSummary> = emptyList(),
    showGroupDialog: Boolean = false,
    isCreatingGroup: Boolean = false,
    banner: Pair<String, Boolean>? = null,
    onBannerDismiss: () -> Unit = {},
    onSearchQueryChange: ((String) -> Unit)? = null,
    onCreateChat: ((String) -> Unit)? = null,
    onOpenGroupDialog: () -> Unit = {},
    onDismissGroupDialog: () -> Unit = {},
    onCreateGroup: (String, List<String>) -> Unit = { _, _ -> },
    onOpenChat: (Conversation) -> Unit,
    /** Opens the other person's profile. Null leaves avatars inert. */
    onOpenProfile: ((String) -> Unit)? = null,
) {
    var localQuery by remember { mutableStateOf("") }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var targetUserIdInput by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

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

    if (showGroupDialog) {
        NewGroupDialog(
            friends = friends,
            isCreating = isCreatingGroup,
            onDismiss = onDismissGroupDialog,
            onCreate = onCreateGroup
        )
    }

    Column(Modifier.fillMaxSize().background(Color.White).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Chats", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Box {
                Text(
                    "＋",
                    color = LinkPurple,
                    fontSize = 28.sp,
                    modifier = Modifier.clickable { showMenu = true }
                )
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Tin nhắn mới") },
                        onClick = {
                            showMenu = false
                            showNewChatDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Tạo nhóm mới") },
                        onClick = {
                            showMenu = false
                            onOpenGroupDialog()
                        }
                    )
                }
            }
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

        if (banner != null) {
            Spacer(Modifier.height(10.dp))
            ChatBanner(banner.first, banner.second, onBannerDismiss)
        }

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
                        Icon(LinkUpIcons.Chat, null, tint = LinkPurple, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Chưa có cuộc trò chuyện nào",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1E1B2E)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Bấm dấu ＋ góc trên để bắt đầu một cuộc trò chuyện hoặc tạo nhóm mới!",
                            fontSize = 13.sp,
                            color = LinkMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showNewChatDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = LinkPurple)
                            ) {
                                Text("Tin nhắn mới", color = Color.White)
                            }
                            TextButton(onClick = onOpenGroupDialog) {
                                Text("Tạo nhóm", color = LinkPurple, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp)) {
                items(filtered, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        isPeerTyping = conversation.id in typingConversationIds,
                        onClick = { onOpenChat(conversation) },
                        onOpenProfile = onOpenProfile
                    )
                    HorizontalDivider(color = LinkDivider, modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    isPeerTyping: Boolean,
    onClick: () -> Unit,
    onOpenProfile: ((String) -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val peerId = conversation.user.id.takeIf {
            onOpenProfile != null && !conversation.isGroup
        }
        Box(
            Modifier.then(
                if (peerId != null) {
                    Modifier.clickable { onOpenProfile?.invoke(peerId) }
                } else {
                    Modifier
                }
            )
        ) {
            if (conversation.isGroup) {
                GroupAvatar(
                    members = conversation.others.take(2).map { it.avatarUrl to it.initials },
                    fallbackInitials = conversation.user.initials,
                    size = 48.dp
                )
            } else {
                CircleAvatar(conversation.user.avatarUrl, conversation.user.initials, 48.dp)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(conversation.user.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isPeerTyping) {
                Text("đang nhập…", color = LinkPurple, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            } else {
                Text(
                    conversation.preview,
                    color = LinkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp
                )
            }
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
}

@Composable
fun ChatDetailScreen(
    title: String = "Chat",
    messages: List<Message>,
    isPeerTyping: Boolean = false,
    isPeerOnline: Boolean = false,
    isLoadingOlder: Boolean = false,
    isGroup: Boolean = false,
    avatarUrl: String? = null,
    initials: String = "C",
    groupFaces: List<Pair<String?, String>> = emptyList(),
    participantAvatars: Map<String, Pair<String?, String>> = emptyMap(),
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onTyping: ((Boolean) -> Unit)? = null,
    onSendImage: ((Uri) -> Unit)? = null,
    onDeleteMessage: ((String) -> Unit)? = null,
    onLoadOlder: (() -> Unit)? = null,
    /** Opens the person you are talking to, from the header title or avatar. */
    onOpenProfile: (() -> Unit)? = null,
) {
    var draft by remember { mutableStateOf("") }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val messageListState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) onSendImage?.invoke(uri) }

    // Only follow new messages when the user is already near the bottom. Scrolling to the
    // end on every size change fought pagination: prepending older rows grows the list and
    // would yank the view back down.
    val newestId = messages.lastOrNull()?.id
    LaunchedEffect(newestId, imeBottom) {
        if (messages.isEmpty()) return@LaunchedEffect
        val lastVisible = messageListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val nearBottom = lastVisible >= messages.size - 3
        if (nearBottom || messageListState.layoutInfo.totalItemsCount == 0) {
            messageListState.animateScrollToItem(messages.size - 1)
        }
    }

    // Reaching the top asks for the previous page.
    LaunchedEffect(messageListState, messages.size) {
        snapshotFlow { messageListState.firstVisibleItemIndex }
            .collect { firstVisible ->
                if (firstVisible <= 1 && messages.isNotEmpty()) onLoadOlder?.invoke()
            }
    }

    LaunchedEffect(draft) {
        onTyping?.invoke(draft.isNotBlank())
    }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Xóa tin nhắn?", fontWeight = FontWeight.Bold) },
            text = { Text("Tin nhắn sẽ bị xóa với tất cả mọi người.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteId?.let { onDeleteMessage?.invoke(it) }
                        pendingDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LinkPurple)
                ) {
                    Text("Xóa", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Hủy", color = LinkMuted)
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().background(LinkCanvas).imePadding()) {
        ChatDetailHeader(
            title = title,
            subtitle = when {
                isPeerTyping -> "đang nhập…"
                isPeerOnline -> "Đang hoạt động"
                else -> null
            },
            isGroup = isGroup,
            avatarUrl = avatarUrl,
            initials = initials,
            memberFaces = groupFaces,
            onBack = onBack,
            onOpenProfile = onOpenProfile
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = messageListState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = false
        ) {
            if (isLoadingOlder) {
                item(key = "older-spinner") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = LinkPurple, modifier = Modifier.size(18.dp))
                    }
                }
            }
            itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                // In a group, only the first message of a run gets a name and an avatar.
                val previous = messages.getOrNull(index - 1)
                val startsRun = previous == null || previous.senderId != message.senderId
                val face = participantAvatars[message.senderId]

                val dayKey = ChatTime.dayKey(message.createdAt)
                val previousDayKey = previous?.let { ChatTime.dayKey(it.createdAt) }
                if (dayKey != null && dayKey != previousDayKey) {
                    ChatTime.dayLabel(message.createdAt)?.let { DaySeparator(it) }
                }

                DomainMessageBubble(
                    message = message,
                    showSenderName = isGroup && startsRun && !message.fromMe,
                    showAvatar = startsRun && !message.fromMe,
                    senderAvatarUrl = face?.first,
                    senderInitials = face?.second ?: message.senderName?.take(1)?.uppercase() ?: "?",
                    onLongPress = if (message.fromMe && onDeleteMessage != null) {
                        { pendingDeleteId = message.id }
                    } else null,
                    onOpenProfile = onOpenProfile
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = LinkUpIcons.Plus,
                contentDescription = "Attach image",
                tint = LinkPurple,
                modifier = Modifier
                    .clickable {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .padding(end = 6.dp)
                    .size(24.dp)
            )
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

/** Centered "Hôm nay" / "Hôm qua" / date chip between messages from different days. */
@Composable
private fun DaySeparator(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = LinkMuted,
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Header with the avatar of whoever you are talking to, plus a live typing subtitle. */
@Composable
private fun ChatDetailHeader(
    title: String,
    subtitle: String?,
    isGroup: Boolean,
    avatarUrl: String?,
    initials: String,
    memberFaces: List<Pair<String?, String>>,
    onBack: () -> Unit,
    onOpenProfile: (() -> Unit)? = null,
) {
    Surface(shadowElevation = 1.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "‹",
                    fontSize = 34.sp,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp)
                )
                Box(
                    Modifier.then(
                        if (onOpenProfile != null) Modifier.clickable(onClick = onOpenProfile) else Modifier
                    )
                ) {
                    if (isGroup) {
                        GroupAvatar(memberFaces, initials, 36.dp)
                    } else {
                        CircleAvatar(avatarUrl, initials, 36.dp)
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                        .then(
                            if (onOpenProfile != null) Modifier.clickable(onClick = onOpenProfile) else Modifier
                        )
                ) {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(subtitle, color = LinkPurple, fontSize = 12.sp)
                    }
                }
                Text("•••", color = LinkPurple, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = LinkDivider)
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
        title = "Chat",
        messages = domainMessages,
        isPeerTyping = false,
        initials = "AC",
        onBack = onBack,
        onSend = onSend
    )
}

@Composable
private fun DomainMessageBubble(
    message: Message,
    showSenderName: Boolean = false,
    showAvatar: Boolean = true,
    senderAvatarUrl: String? = null,
    senderInitials: String = "C",
    onLongPress: (() -> Unit)? = null,
    onOpenProfile: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start
    ) {
        if (!message.fromMe) {
            if (showAvatar) {
                Box(
                    Modifier.then(
                        if (onOpenProfile != null) Modifier.clickable(onClick = onOpenProfile) else Modifier
                    )
                ) {
                    CircleAvatar(senderAvatarUrl, senderInitials, 30.dp)
                }
            } else {
                Spacer(Modifier.width(30.dp))
            }
        }
        Column(
            Modifier
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 7.dp)
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        16.dp,
                        16.dp,
                        if (message.fromMe) 3.dp else 16.dp,
                        if (message.fromMe) 16.dp else 3.dp
                    )
                )
                .background(if (message.fromMe) LinkPurple else Color.White)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (showSenderName) {
                Text(
                    message.senderName ?: senderInitials,
                    color = LinkPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (message.type == "IMAGE" && !message.mediaUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = message.mediaUrl,
                    contentDescription = "Ảnh",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                message.textContent?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = if (message.fromMe) Color.White else Color(0xFF1E1B2E))
                }
            } else if (!message.textContent.isNullOrBlank()) {
                val text = message.textContent ?: ""
                Text(
                    text,
                    color = if (message.fromMe) Color.White else Color(0xFF1E1B2E)
                )
            }
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    ChatTime.clock(message.createdAt),
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

/**
 * Group creation: a name plus at least two friends picked from the friend list.
 *
 * Members come from the friend list rather than a free-text ID field — you should not
 * have to know someone's UUID to start a group with them.
 */
@Composable
private fun NewGroupDialog(
    friends: List<UserSummary>,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit,
) {
    var groupName by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val canCreate = groupName.isNotBlank() && selected.size >= 2 && !isCreating

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo nhóm mới", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = { Text("Tên nhóm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Chọn thành viên (tối thiểu 2): ${selected.size} đã chọn",
                    fontSize = 13.sp,
                    color = LinkMuted
                )
                Spacer(Modifier.height(8.dp))
                if (friends.isEmpty()) {
                    Text(
                        "Bạn chưa có bạn bè nào. Hãy thêm bạn trước khi tạo nhóm.",
                        fontSize = 13.sp,
                        color = LinkMuted
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(friends, key = { it.id }) { friend ->
                            val isSelected = friend.id in selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (isSelected) selected - friend.id else selected + friend.id
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isSelected, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                CircleAvatar(friend.avatarUrl, friend.initials, 36.dp)
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(
                                        friend.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(friend.handle, color = LinkMuted, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(groupName.trim(), selected.toList()) },
                enabled = canCreate,
                colors = ButtonDefaults.buttonColors(containerColor = LinkPurple)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text("Tạo nhóm", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = LinkMuted)
            }
        }
    )
}

/** Dismissible strip for "group created" confirmations and failures. */
@Composable
private fun ChatBanner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val foreground = if (isError) Color(0xFFB3261E) else Color(0xFF1B7A43)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isError) Color(0xFFFDECEF) else Color(0xFFE9F7EF))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = foreground, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            "✕",
            color = foreground,
            fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onDismiss).padding(start = 8.dp)
        )
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
