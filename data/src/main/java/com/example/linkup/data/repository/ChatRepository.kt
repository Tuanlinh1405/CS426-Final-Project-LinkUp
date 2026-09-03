package com.example.linkup.data.repository

import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.Message
import com.example.linkup.data.remote.websocket.ChatWebSocketClient
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val conversationsState: StateFlow<List<Conversation>>
    val connectionState: StateFlow<ChatWebSocketClient.ConnectionState>
    val typingEventFlow: SharedFlow<Pair<String, Boolean>>

    fun getMessagesFlow(conversationId: String): StateFlow<List<Message>>
    suspend fun refreshConversations(): Result<List<Conversation>>
    suspend fun loadMessages(conversationId: String, limit: Int = 50, offset: Long = 0): Result<List<Message>>
    suspend fun createDirectConversation(targetUserId: String): Result<Conversation>
    suspend fun createGroupConversation(name: String, memberUserIds: List<String>): Result<Conversation>
    suspend fun sendMessage(conversationId: String, text: String): Result<Message>
    suspend fun markAsRead(conversationId: String)
    suspend fun sendTyping(conversationId: String, isTyping: Boolean)
    fun connectWebSocket()
    fun disconnectWebSocket()
}
