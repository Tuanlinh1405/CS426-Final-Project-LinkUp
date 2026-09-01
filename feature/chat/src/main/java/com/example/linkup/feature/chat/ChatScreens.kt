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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.designsystem.component.Avatar
import com.example.linkup.core.designsystem.component.LinkUpField
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.data.model.ChatMessage
import com.example.linkup.data.model.Conversation
import com.example.linkup.core.designsystem.theme.LinkCanvas
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple

@Composable
fun ChatListScreen(conversations: List<Conversation>, onOpenChat: (Conversation) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = conversations.filter { it.user.name.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize().background(Color.White).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Chats", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text("＋", color = LinkPurple, fontSize = 28.sp)
        }
        LinkUpField(query, { query = it }, "Search conversations", Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp)) {
            items(filtered, key = { it.id }) { conversation ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenChat(conversation) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(conversation.user.initials, 48)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(conversation.user.name, fontWeight = FontWeight.Bold)
                        Text(conversation.preview, color = LinkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(conversation.time, color = LinkMuted, fontSize = 11.sp)
                        if (conversation.unread > 0) Box(Modifier.clip(CircleShape).background(LinkPurple).padding(horizontal = 7.dp, vertical = 3.dp)) {
                            Text(conversation.unread.toString(), color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
                HorizontalDivider(color = LinkDivider, modifier = Modifier.padding(start = 76.dp))
            }
        }
    }
}

@Composable
fun ChatDetailScreen(messages: List<ChatMessage>, onBack: () -> Unit, onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val messageListState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(messages.size, imeBottom) {
        if (messages.isNotEmpty()) messageListState.animateScrollToItem(messages.size)
    }
    Column(Modifier.fillMaxSize().background(LinkCanvas).imePadding()) {
        ScreenHeader("Alex Chen · online", onBack, action = "•••")
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = messageListState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = false
        ) {
            item { Text("Today", color = LinkMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            items(messages, key = { it.id }) { message -> MessageBubble(message) }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("＋", color = LinkPurple, fontSize = 26.sp, modifier = Modifier.padding(end = 6.dp))
            LinkUpField(draft, { draft = it }, "Type a message", Modifier.weight(1f))
            Text("➤", color = LinkPurple, fontSize = 24.sp, modifier = Modifier.clickable {
                if (draft.isNotBlank()) { onSend(draft); draft = "" }
            }.padding(start = 10.dp))
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
        if (!message.fromMe) Avatar("AC", 30)
        Column(
            Modifier.padding(horizontal = 7.dp).widthIn(max = 280.dp).clip(
                RoundedCornerShape(16.dp, 16.dp, if (message.fromMe) 3.dp else 16.dp, if (message.fromMe) 16.dp else 3.dp)
            ).background(if (message.fromMe) LinkPurple else Color.White).padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(message.text, color = if (message.fromMe) Color.White else Color(0xFF1E1B2E))
            Text("${message.time}  ${if (message.fromMe) "✓✓" else ""}", color = if (message.fromMe) Color.White.copy(alpha = .7f) else LinkMuted, fontSize = 9.sp, modifier = Modifier.align(Alignment.End))
        }
    }
}
