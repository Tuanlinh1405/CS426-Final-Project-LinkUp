package com.example.linkup.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.designsystem.component.LinkUpField
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.core.designsystem.theme.LinkCanvas
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft

data class AiBubble(val id: Int, val text: String, val fromMe: Boolean)

@Composable
fun AiChatScreen(onBack: () -> Unit, onHistory: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    var messages by remember {
        mutableStateOf(listOf(AiBubble(1, "Hi Sarah! I’m LinkUp AI. I can help you write posts, brainstorm ideas, or answer questions.", false)))
    }
    val messageListState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(messages.size, imeBottom) {
        if (messages.isNotEmpty()) messageListState.animateScrollToItem(messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().background(LinkCanvas).imePadding()) {
        ScreenHeader("LinkUp AI", onBack, action = "History", onAction = onHistory)
        LazyColumn(Modifier.weight(1f).padding(12.dp), state = messageListState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
                    if (!message.fromMe) Box(Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(LinkPurple, Color(0xFFFF4D98)))).padding(10.dp)) { Text("AI", color = Color.White, fontWeight = FontWeight.Bold) }
                    Text(
                        message.text,
                        color = if (message.fromMe) Color.White else Color(0xFF1E1B2E),
                        modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth(.78f).clip(RoundedCornerShape(16.dp)).background(if (message.fromMe) LinkPurple else Color.White).padding(14.dp)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            LinkUpField(draft, { draft = it }, "Ask LinkUp AI…", Modifier.weight(1f))
            Text("➤", color = LinkPurple, fontSize = 24.sp, modifier = Modifier.clickable {
                if (draft.isNotBlank()) {
                    val prompt = draft.trim()
                    val next = messages.size + 1
                    messages = messages + AiBubble(next, prompt, true) + AiBubble(next + 1, "Mock mode: Here’s a polished starting point for “$prompt”. Connect the backend AI provider to replace this response.", false)
                    draft = ""
                }
            }.padding(start = 10.dp))
        }
    }
}

@Composable
fun AiConversationsScreen(onBack: () -> Unit, onOpen: () -> Unit) {
    val conversations = listOf("Ideas for my launch post" to "Today", "Travel reel captions" to "Yesterday", "Weekly planning" to "Aug 24")
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("AI Conversations", onBack)
        Text("Recent", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        conversations.forEach { (title, date) ->
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(CircleShape).background(LinkPurpleSoft).padding(12.dp)) { Text("AI", color = LinkPurple, fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text("Tap to continue this conversation", color = LinkMuted, fontSize = 12.sp)
                }
                Text(date, color = LinkMuted, fontSize = 11.sp)
            }
        }
    }
}
