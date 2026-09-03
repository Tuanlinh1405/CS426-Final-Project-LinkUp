package com.example.linkup.data.repository

import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.Message
import com.example.linkup.data.model.PickedImage
import com.example.linkup.data.remote.websocket.ChatWebSocketClient
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val conversationsState: StateFlow<List<Conversation>>
    val connectionState: StateFlow<ChatWebSocketClient.ConnectionState>
    val typingEventFlow: SharedFlow<Pair<String, Boolean>>

    /** Conversations where someone else is typing right now; drives the chat-list hint. */
    val typingConversationIds: StateFlow<Set<String>>

    /** Users known to have a live socket. Fed by PRESENCE frames and [loadPresence]. */
    val onlineUserIds: StateFlow<Set<String>>

    /**
     * The conversation the user currently has open. Incoming messages there are not
     * counted as unread and the thread is auto-marked read, because the user can see them.
     */
    fun setActiveConversation(conversationId: String?)

    fun getMessagesFlow(conversationId: String): StateFlow<List<Message>>
    fun conversationFlow(conversationId: String): StateFlow<Conversation?>
    suspend fun refreshConversations(): Result<List<Conversation>>
    suspend fun loadMessages(conversationId: String, limit: Int = 50, offset: Long = 0): Result<List<Message>>
    suspend fun createDirectConversation(targetUserId: String): Result<Conversation>
    suspend fun createGroupConversation(name: String, memberUserIds: List<String>): Result<Conversation>
    suspend fun sendMessage(conversationId: String, text: String): Result<Message>

    /** Uploads [image], then sends it as an IMAGE message. Shows optimistically while uploading. */
    suspend fun sendImageMessage(conversationId: String, image: PickedImage): Result<Message>

    /** One-shot presence read for when a thread opens; WS frames only carry transitions. */
    suspend fun loadPresence(conversationId: String)

    /** Unsends one of the user's own messages, for everyone. */
    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit>

    suspend fun markAsRead(conversationId: String)
    suspend fun sendTyping(conversationId: String, isTyping: Boolean)
    fun connectWebSocket()
    fun disconnectWebSocket()
}
