package com.example.linkup.data.repository

import android.util.Log
import com.example.linkup.data.local.datastore.AuthTokenDataStore
import com.example.linkup.data.local.datastore.ConversationCacheDataStore
import com.example.linkup.data.mapper.toDomain
import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.Message
import com.example.linkup.data.model.MessageStatus
import com.example.linkup.data.remote.api.ChatApiService
import com.example.linkup.data.remote.dto.CreateDirectConversationRequest
import com.example.linkup.data.remote.dto.CreateGroupConversationRequest
import com.example.linkup.data.remote.dto.SendMessageRequest
import com.example.linkup.data.remote.websocket.ChatWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatApiService: ChatApiService,
    private val webSocketClient: ChatWebSocketClient,
    private val authTokenDataStore: AuthTokenDataStore,
    private val conversationCache: ConversationCacheDataStore,
) : ChatRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentUserId: String? = null
    private var retryJob: Job? = null

    private val _conversationsState = MutableStateFlow<List<Conversation>>(emptyList())
    override val conversationsState: StateFlow<List<Conversation>> = _conversationsState.asStateFlow()

    override val connectionState: StateFlow<ChatWebSocketClient.ConnectionState> =
        webSocketClient.connectionState

    private val _typingEventFlow = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 16)
    override val typingEventFlow: SharedFlow<Pair<String, Boolean>> = _typingEventFlow.asSharedFlow()

    private val messagesMap = ConcurrentHashMap<String, MutableStateFlow<List<Message>>>()

    init {
        scope.launch {
            authTokenDataStore.userIdFlow.collect { id ->
                if (currentUserId != id) {
                    currentUserId = id
                    _conversationsState.value = emptyList()
                    messagesMap.clear()
                    if (id != null) {
                        // Render the last-known list instantly from disk while the network
                        // refresh below loads; avoids the "no chats yet" flash on every entry.
                        val cached = conversationCache.read(id)
                        if (cached.isNotEmpty()) {
                            _conversationsState.value = cached.map { it.toDomain(id) }
                        }
                        refreshConversations()
                    } else {
                        disconnectWebSocket()
                    }
                }
            }
        }

        scope.launch {
            webSocketClient.incomingFrames.collect { frame ->
                handleWebSocketFrame(frame)
            }
        }

        scope.launch {
            webSocketClient.connectionState.collect { state ->
                if (state == ChatWebSocketClient.ConnectionState.CONNECTED && _conversationsState.value.isEmpty()) {
                    refreshConversations()
                }
            }
        }
    }

    override fun getMessagesFlow(conversationId: String): StateFlow<List<Message>> {
        return messagesMap.getOrPut(conversationId) {
            MutableStateFlow(emptyList())
        }.asStateFlow()
    }

    override suspend fun refreshConversations(): Result<List<Conversation>> {
        return try {
            if (currentUserId == null) {
                currentUserId = authTokenDataStore.userIdFlow.firstOrNull()
            }
            val response = chatApiService.getConversations()
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!
                val domainList = dtos.map { it.toDomain(currentUserId) }
                _conversationsState.value = domainList
                currentUserId?.let { uid ->
                    scope.launch { conversationCache.write(uid, dtos) }
                }
                connectWebSocket()
                Result.success(domainList)
            } else {
                Log.e(TAG, "Failed to fetch conversations: ${response.code()}")
                Result.failure(Exception("Failed to fetch conversations: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing conversations, scheduling auto-retry...", e)
            scheduleConversationsRetry()
            Result.failure(e)
        }
    }

    // A single in-flight retry chain; recursive retries used to stack up and flood the backend.
    private fun scheduleConversationsRetry() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            delay(RETRY_DELAY_MS)
            refreshConversations()
        }
    }

    override suspend fun loadMessages(
        conversationId: String,
        limit: Int,
        offset: Long,
    ): Result<List<Message>> {
        return try {
            if (currentUserId == null) {
                currentUserId = authTokenDataStore.userIdFlow.firstOrNull()
            }
            val response = chatApiService.getMessages(conversationId, limit, offset)
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!
                val domainMessages = dtos.map { it.toDomain(currentUserId) }

                val flow = messagesMap.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
                if (offset == 0L) {
                    flow.value = domainMessages
                } else {
                    val existing = flow.value
                    val combined = (existing + domainMessages).distinctBy { it.id }
                    flow.value = combined
                }

                Result.success(domainMessages)
            } else {
                Result.failure(Exception("Failed to load messages: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading messages for conversation $conversationId", e)
            Result.failure(e)
        }
    }

    override suspend fun createDirectConversation(targetUserId: String): Result<Conversation> {
        return try {
            val response = chatApiService.createDirectConversation(
                CreateDirectConversationRequest(targetUserId)
            )
            if (response.isSuccessful && response.body() != null) {
                val conv = response.body()!!.toDomain(currentUserId)
                val currentList = _conversationsState.value
                val updatedList = listOf(conv) + currentList.filter { it.id != conv.id }
                _conversationsState.value = updatedList
                Result.success(conv)
            } else {
                Result.failure(Exception("Failed to create direct conversation: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating direct conversation with $targetUserId", e)
            Result.failure(e)
        }
    }

    override suspend fun createGroupConversation(
        name: String,
        memberUserIds: List<String>,
    ): Result<Conversation> {
        return try {
            val response = chatApiService.createGroupConversation(
                CreateGroupConversationRequest(name, memberUserIds)
            )
            if (response.isSuccessful && response.body() != null) {
                val conv = response.body()!!.toDomain(currentUserId)
                val currentList = _conversationsState.value
                val updatedList = listOf(conv) + currentList.filter { it.id != conv.id }
                _conversationsState.value = updatedList
                Result.success(conv)
            } else {
                Result.failure(Exception("Failed to create group conversation: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating group conversation $name", e)
            Result.failure(e)
        }
    }

    override suspend fun sendMessage(conversationId: String, text: String): Result<Message> {
        val tempId = UUID.randomUUID().toString()
        val nowIso = getCurrentIsoTimestamp()

        val optimisticMsg = Message(
            id = tempId,
            conversationId = conversationId,
            senderId = currentUserId ?: "",
            type = "TEXT",
            textContent = text,
            status = MessageStatus.SENT,
            createdAt = nowIso,
            fromMe = true
        )

        val flow = messagesMap.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        flow.value = flow.value + optimisticMsg

        updateConversationLastMessage(conversationId, optimisticMsg)

        val wsSent = webSocketClient.sendMessage(
            conversationId = conversationId,
            textContent = text,
            type = "TEXT",
            tempId = tempId
        )

        if (wsSent) {
            return Result.success(optimisticMsg)
        }

        return try {
            val response = chatApiService.sendMessage(
                conversationId,
                SendMessageRequest(textContent = text, type = "TEXT", tempId = tempId)
            )
            if (response.isSuccessful && response.body() != null) {
                val serverMsg = response.body()!!.toDomain(currentUserId)
                val currentMsgs = flow.value
                flow.value = currentMsgs.map { if (it.id == tempId) serverMsg else it }
                updateConversationLastMessage(conversationId, serverMsg)
                Result.success(serverMsg)
            } else {
                Result.success(optimisticMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "REST fallback send message failed", e)
            Result.success(optimisticMsg)
        }
    }

    override suspend fun markAsRead(conversationId: String) {
        webSocketClient.markRead(conversationId)

        try {
            chatApiService.markAsRead(conversationId)
        } catch (e: Exception) {
            Log.e(TAG, "Error calling REST markAsRead", e)
        }

        val flow = messagesMap[conversationId]
        if (flow != null) {
            flow.value = flow.value.map { msg ->
                if (!msg.fromMe && msg.status != MessageStatus.SEEN) {
                    msg.copy(status = MessageStatus.SEEN)
                } else msg
            }
        }

        val currentConvs = _conversationsState.value
        _conversationsState.value = currentConvs.map { conv ->
            if (conv.id == conversationId) {
                conv.copy(unreadCount = 0, unread = 0)
            } else conv
        }
    }

    override suspend fun sendTyping(conversationId: String, isTyping: Boolean) {
        webSocketClient.sendTyping(conversationId, isTyping)
    }

    override fun connectWebSocket() {
        webSocketClient.connect()
    }

    override fun disconnectWebSocket() {
        webSocketClient.disconnect()
    }

    private fun handleWebSocketFrame(frame: com.example.linkup.data.remote.dto.WebSocketFrameDto) {
        val convId = frame.conversationId ?: return

        when (frame.event) {
            "MESSAGE_RECEIVED" -> {
                val msgDto = frame.message ?: return
                val incomingMsg = msgDto.toDomain(currentUserId)

                val flow = messagesMap.getOrPut(convId) { MutableStateFlow(emptyList()) }
                val currentMsgs = flow.value

                val existingIndex = currentMsgs.indexOfFirst {
                    it.id == incomingMsg.id || (frame.tempId != null && it.id == frame.tempId)
                }

                if (existingIndex >= 0) {
                    val mutable = currentMsgs.toMutableList()
                    mutable[existingIndex] = incomingMsg
                    flow.value = mutable
                } else {
                    flow.value = currentMsgs + incomingMsg
                }

                updateConversationLastMessage(convId, incomingMsg, isIncoming = !incomingMsg.fromMe)
            }

            "MESSAGE_STATUS_UPDATE" -> {
                val targetMsgId = frame.messageId ?: frame.message?.id
                val targetTempId = frame.tempId
                val newStatusStr = frame.status ?: frame.message?.status
                val newStatus = MessageStatus.fromString(newStatusStr)

                val flow = messagesMap[convId]
                if (flow != null && (targetMsgId != null || targetTempId != null)) {
                    flow.value = flow.value.map { msg ->
                        if (msg.id == targetMsgId || (targetTempId != null && msg.id == targetTempId)) {
                            msg.copy(status = newStatus)
                        } else msg
                    }
                }

                val currentConvs = _conversationsState.value
                _conversationsState.value = currentConvs.map { conv ->
                    if (conv.id == convId && conv.lastMessage != null &&
                        (conv.lastMessage.id == targetMsgId || conv.lastMessage.id == targetTempId)
                    ) {
                        conv.copy(lastMessage = conv.lastMessage.copy(status = newStatus))
                    } else conv
                }
            }

            "TYPING" -> {
                val isTyping = frame.isTyping ?: false
                _typingEventFlow.tryEmit(Pair(convId, isTyping))
            }
        }
    }

    private fun updateConversationLastMessage(
        conversationId: String,
        lastMsg: Message,
        isIncoming: Boolean = false,
    ) {
        val currentConvs = _conversationsState.value
        val existingIndex = currentConvs.indexOfFirst { it.id == conversationId }

        if (existingIndex >= 0) {
            val conv = currentConvs[existingIndex]
            val newUnread = if (isIncoming) conv.unreadCount + 1 else conv.unreadCount
            val updatedConv = conv.copy(
                lastMessage = lastMsg,
                preview = lastMsg.textContent ?: "Media message",
                time = lastMsg.createdAt.substringAfter("T").take(5).ifEmpty { "Now" },
                unreadCount = newUnread,
                unread = newUnread,
                updatedAt = lastMsg.createdAt
            )

            val mutable = currentConvs.toMutableList()
            mutable.removeAt(existingIndex)
            mutable.add(0, updatedConv)
            _conversationsState.value = mutable
        } else {
            scope.launch {
                refreshConversations()
            }
        }
    }

    private fun getCurrentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    companion object {
        private const val TAG = "ChatRepositoryImpl"
        private const val RETRY_DELAY_MS = 2500L
    }
}
