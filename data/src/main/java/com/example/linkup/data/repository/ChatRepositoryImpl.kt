package com.example.linkup.data.repository

import android.util.Log
import com.example.linkup.data.local.datastore.AuthTokenDataStore
import com.example.linkup.data.local.datastore.ConversationCacheDataStore
import com.example.linkup.data.mapper.buildPreview
import com.example.linkup.data.mapper.toDomain
import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.Message
import com.example.linkup.data.model.MessageStatus
import com.example.linkup.data.model.PickedImage
import com.example.linkup.data.remote.api.ChatApiService
import com.example.linkup.data.remote.dto.CreateDirectConversationRequest
import com.example.linkup.data.remote.dto.CreateGroupConversationRequest
import com.example.linkup.data.remote.dto.SendMessageRequest
import com.example.linkup.data.remote.websocket.ChatWebSocketClient
import com.example.linkup.data.util.ChatTime
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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
    private var listRefreshJob: Job? = null

    // The conversation the user currently has open. Null when on the list screen.
    // Messages arriving here skip unread count and are auto-marked-read.
    @Volatile
    private var activeConversationId: String? = null

    override fun setActiveConversation(conversationId: String?) {
        activeConversationId = conversationId
    }

    private val _conversationsState = MutableStateFlow<List<Conversation>>(emptyList())
    override val conversationsState: StateFlow<List<Conversation>> = _conversationsState.asStateFlow()

    override val connectionState: StateFlow<ChatWebSocketClient.ConnectionState> =
        webSocketClient.connectionState

    private val _typingEventFlow = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 16)
    override val typingEventFlow: SharedFlow<Pair<String, Boolean>> = _typingEventFlow.asSharedFlow()

    private val _typingConversationIds = MutableStateFlow<Set<String>>(emptySet())
    override val typingConversationIds: StateFlow<Set<String>> = _typingConversationIds.asStateFlow()

    private val _onlineUserIds = MutableStateFlow<Set<String>>(emptySet())
    override val onlineUserIds: StateFlow<Set<String>> = _onlineUserIds.asStateFlow()

    // A peer that goes quiet (or drops offline) never sends TYPING=false, so each
    // conversation carries its own expiry job instead of trusting the peer to clear it.
    private val typingExpiryJobs = ConcurrentHashMap<String, Job>()

    private val messagesMap = ConcurrentHashMap<String, MutableStateFlow<List<Message>>>()
    private val conversationMap = ConcurrentHashMap<String, MutableStateFlow<Conversation?>>()

    // conversationId -> id of the newest message that was already read locally.
    // The server's unread_count and our markAsRead race through Supabase, so a refresh
    // that lands after the read would otherwise restore the badge we just cleared.
    private val readUpToMessageId = ConcurrentHashMap<String, String>()

    init {
        scope.launch {
            authTokenDataStore.userIdFlow.collect { id ->
                if (currentUserId != id) {
                    currentUserId = id
                    _conversationsState.value = emptyList()
                    messagesMap.clear()
                    conversationMap.clear()
                    readUpToMessageId.clear()
                    _typingConversationIds.value = emptySet()
                    _onlineUserIds.value = emptySet()
                    activeConversationId = null
                    if (id != null) {
                        // Render the last-known list instantly from disk while the network
                        // refresh below loads; avoids the "no chats yet" flash on every entry.
                        val cached = conversationCache.read(id)
                        if (cached.isNotEmpty()) {
                            publishConversations(cached.map { it.toDomain(id) })
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

    override fun conversationFlow(conversationId: String): StateFlow<Conversation?> {
        return conversationMap.getOrPut(conversationId) {
            MutableStateFlow(_conversationsState.value.firstOrNull { it.id == conversationId })
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
                val domainList = dtos.map { it.toDomain(currentUserId) }.map { applyLocalRead(it) }
                publishConversations(domainList)
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

    /**
     * Single writer for the list. Sorting here (newest first) means a realtime update and
     * a fresh fetch always agree on order, and the per-conversation flows that the detail
     * screen observes are refreshed from the same data.
     */
    private fun publishConversations(list: List<Conversation>) {
        // Sort on parsed instants, not the raw strings: the server emits second-precision
        // stamps for some rows and millisecond precision for others, which do not compare
        // lexicographically in the right order.
        val sorted = list.sortedByDescending { ChatTime.parseMillis(it.updatedAt) ?: 0L }
        _conversationsState.value = sorted
        for (conv in sorted) {
            conversationMap[conv.id]?.value = conv
        }
    }

    /**
     * A server refresh can carry a stale unread_count if it raced a markAsRead that
     * hasn't committed to Supabase yet. Everything up to [readUpToMessageId] was read on
     * this device, so recount from the local thread instead of trusting the server number.
     */
    private fun applyLocalRead(conv: Conversation): Conversation {
        val readUpTo = readUpToMessageId[conv.id] ?: return conv
        val known = messagesMap[conv.id]?.value
        val cutoff = known?.indexOfFirst { it.id == readUpTo } ?: -1

        val unread = when {
            cutoff >= 0 -> known!!.drop(cutoff + 1).count { !it.fromMe }
            conv.lastMessage == null || conv.lastMessage.fromMe || conv.lastMessage.id == readUpTo -> 0
            else -> return conv
        }
        return if (unread < conv.unreadCount) conv.copy(unreadCount = unread, unread = unread) else conv
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
                    // The server pages backwards from the newest message, so this batch is
                    // OLDER than what is on screen: prepend it. Appending put yesterday's
                    // messages under today's. Re-sorting keeps optimistic sends last.
                    val existing = flow.value
                    flow.value = (domainMessages + existing)
                        .distinctBy { it.id }
                        .sortedBy { ChatTime.parseMillis(it.createdAt) ?: Long.MAX_VALUE }
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
                publishConversations(listOf(conv) + _conversationsState.value.filter { it.id != conv.id })
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
                publishConversations(listOf(conv) + _conversationsState.value.filter { it.id != conv.id })
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

    override suspend fun sendImageMessage(conversationId: String, image: PickedImage): Result<Message> {
        val tempId = UUID.randomUUID().toString()
        val nowIso = getCurrentIsoTimestamp()

        // Show the bubble immediately; the server URL is swapped in on the ACK.
        val optimisticMsg = Message(
            id = tempId,
            conversationId = conversationId,
            senderId = currentUserId ?: "",
            type = "IMAGE",
            textContent = null,
            status = MessageStatus.SENT,
            createdAt = nowIso,
            fromMe = true
        )

        val flow = messagesMap.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        flow.value = flow.value + optimisticMsg
        updateConversationLastMessage(conversationId, optimisticMsg)

        // 1. Upload the image; the conversation URL points at the stored file.
        val upload = runCatching {
            chatApiService.uploadChatMedia(
                conversationId,
                MultipartBody.Part.createFormData(
                    "file",
                    image.fileName,
                    image.bytes.toRequestBody(image.mimeType.toMediaTypeOrNull())
                )
            )
        }.getOrNull()

        val mediaUrl = upload?.takeIf { it.isSuccessful }?.body()?.url
        if (mediaUrl == null) {
            // Upload failed: drop the optimistic bubble rather than strand a broken one.
            flow.value = flow.value.filterNot { it.id == tempId }
            return Result.failure(Exception("Upload failed"))
        }

        // 2. Send over WS with the resolved URL (mirror the text path's REST fallback).
        val wsSent = webSocketClient.sendMessage(
            conversationId = conversationId,
            textContent = null,
            type = "IMAGE",
            tempId = tempId,
            mediaUrl = mediaUrl
        )

        if (wsSent) {
            val urlMsg = optimisticMsg.copy(mediaUrl = mediaUrl)
            flow.value = flow.value.map { if (it.id == tempId) urlMsg else it }
            updateConversationLastMessage(conversationId, urlMsg)
            return Result.success(urlMsg)
        }

        return try {
            val response = chatApiService.sendMessage(
                conversationId,
                SendMessageRequest(type = "IMAGE", mediaUrl = mediaUrl, tempId = tempId)
            )
            if (response.isSuccessful && response.body() != null) {
                val serverMsg = response.body()!!.toDomain(currentUserId)
                val currentMsgs = flow.value
                flow.value = currentMsgs.map { if (it.id == tempId) serverMsg else it }
                updateConversationLastMessage(conversationId, serverMsg)
                Result.success(serverMsg)
            } else {
                val urlMsg = optimisticMsg.copy(mediaUrl = mediaUrl)
                flow.value = flow.value.map { if (it.id == tempId) urlMsg else it }
                Result.success(urlMsg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "REST fallback image send failed", e)
            Result.success(optimisticMsg.copy(mediaUrl = mediaUrl))
        }
    }

    override suspend fun loadPresence(conversationId: String) {
        try {
            val response = chatApiService.getPresence(conversationId)
            if (response.isSuccessful && response.body() != null) {
                _onlineUserIds.value = response.body()!!.onlineUserIds.toSet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading presence for conversation $conversationId", e)
        }
    }

    override suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> {
        return try {
            val response = chatApiService.deleteMessage(conversationId, messageId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete message: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting message $messageId", e)
            Result.failure(e)
        }
    }

    override suspend fun markAsRead(conversationId: String) {
        // WS and REST hit the same handler, so sending both made the server mark the
        // conversation twice and broadcast a duplicate set of SEEN frames.
        if (!webSocketClient.markRead(conversationId)) {
            try {
                chatApiService.markAsRead(conversationId)
            } catch (e: Exception) {
                Log.e(TAG, "REST fallback markAsRead failed", e)
            }
        }

        val flow = messagesMap[conversationId]
        if (flow != null) {
            val now = ChatTime.nowIso()
            flow.value = flow.value.map { msg ->
                if (!msg.fromMe && msg.status != MessageStatus.SEEN) {
                    msg.copy(status = MessageStatus.SEEN, createdAt = msg.createdAt.ifEmpty { now })
                } else msg
            }
        }

        val currentConvs = _conversationsState.value
        val readUpTo = messagesMap[conversationId]?.value?.lastOrNull()?.id
            ?: currentConvs.firstOrNull { it.id == conversationId }?.lastMessage?.id
        if (readUpTo != null) readUpToMessageId[conversationId] = readUpTo

        publishConversations(
            currentConvs.map { conv ->
                if (conv.id == conversationId) conv.copy(unreadCount = 0, unread = 0) else conv
            }
        )
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
        // Presence is per-user, not per-conversation, so it carries no conversationId and
        // has to be handled before the guard below.
        if (frame.event == "PRESENCE") {
            val peerId = frame.senderId ?: return
            val isOnline = frame.status == "ONLINE"
            _onlineUserIds.update { current ->
                if (isOnline) current + peerId else current - peerId
            }
            return
        }

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

                // The user is looking at this thread, so the message is already read: tell
                // the server (which sends SEEN back to the sender) instead of badging it.
                if (!incomingMsg.fromMe && convId == activeConversationId) {
                    scope.launch { markAsRead(convId) }
                }
            }

            "MESSAGE_STATUS_UPDATE" -> {
                val targetMsgId = frame.messageId ?: frame.message?.id
                val targetTempId = frame.tempId
                val newStatusStr = frame.status ?: frame.message?.status
                val newStatus = MessageStatus.fromString(newStatusStr)

                val flow = messagesMap.getOrPut(convId) { MutableStateFlow(emptyList()) }
                if (targetMsgId != null || targetTempId != null) {
                    flow.value = flow.value.map { msg ->
                        when {
                            targetTempId != null && msg.id == targetTempId && targetMsgId != null ->
                                // Replace tempId with the real server ID and update status
                                msg.copy(id = targetMsgId, status = newStatus)
                            msg.id == targetMsgId -> msg.copy(status = newStatus)
                            targetTempId != null && msg.id == targetTempId -> msg.copy(status = newStatus)
                            else -> msg
                        }
                    }
                }

                val currentConvs = _conversationsState.value
                publishConversations(
                    currentConvs.map { conv ->
                        if (conv.id == convId && conv.lastMessage != null &&
                            (conv.lastMessage.id == targetMsgId || conv.lastMessage.id == targetTempId)
                        ) {
                            val updatedLast = conv.lastMessage.let { lm ->
                                if (targetTempId != null && lm.id == targetTempId && targetMsgId != null) {
                                    lm.copy(id = targetMsgId, status = newStatus)
                                } else {
                                    lm.copy(status = newStatus)
                                }
                            }
                            conv.copy(lastMessage = updatedLast)
                        } else conv
                    }
                )
            }

            "TYPING" -> {
                val isTyping = frame.isTyping ?: false
                _typingEventFlow.tryEmit(Pair(convId, isTyping))
                setTyping(convId, isTyping)
            }

            // Someone created a conversation with us (direct or group) while we were
            // online; the list has no row for it yet, so pull the fresh list.
            "CONVERSATION_CREATED" -> {
                scheduleListRefresh()
            }

            "MESSAGE_DELETED" -> {
                val msgId = frame.messageId ?: return
                val flow = messagesMap.getOrPut(convId) { MutableStateFlow(emptyList()) }
                val current = flow.value
                if (current.none { it.id == msgId }) return

                val remaining = current.filterNot { it.id == msgId }
                flow.value = remaining

                // Recompute the conversation row's preview and last message from
                // whatever is left, or wipe them when the thread goes empty.
                val currentConvs = _conversationsState.value
                val conv = currentConvs.firstOrNull { it.id == convId } ?: return
                val newLast = remaining.lastOrNull()
                publishConversations(
                    currentConvs.map {
                        if (it.id == convId) it.copy(
                            lastMessage = newLast,
                            preview = buildPreview(it.isGroup, newLast, it.others),
                            time = ChatTime.listStamp(newLast?.createdAt ?: it.updatedAt)
                        ) else it
                    }
                )
            }
        }
    }

    /** Marks a conversation as "peer typing" and arms an expiry so it always clears. */
    private fun setTyping(conversationId: String, isTyping: Boolean) {
        typingExpiryJobs.remove(conversationId)?.cancel()
        _typingConversationIds.update { current ->
            if (isTyping) current + conversationId else current - conversationId
        }
        if (isTyping) {
            typingExpiryJobs[conversationId] = scope.launch {
                delay(TYPING_EXPIRY_MS)
                _typingConversationIds.update { it - conversationId }
                typingExpiryJobs.remove(conversationId)
            }
        }
    }

    private fun updateConversationLastMessage(
        conversationId: String,
        lastMsg: Message,
        isIncoming: Boolean = false,
    ) {
        val currentConvs = _conversationsState.value
        val existing = currentConvs.firstOrNull { it.id == conversationId }

        if (existing == null) {
            // First message of a conversation this client has never loaded (someone
            // started a chat or added us to a group): fetch it rather than guess a title.
            scheduleListRefresh()
            return
        }

        // Messages in the open thread are seen instantly: don't badge the list row.
        val newUnread = if (isIncoming && conversationId != activeConversationId) {
            existing.unreadCount + 1
        } else {
            existing.unreadCount
        }
        val updated = existing.copy(
            lastMessage = lastMsg,
            preview = buildPreview(existing.isGroup, lastMsg, existing.others),
            time = ChatTime.listStamp(lastMsg.createdAt),
            unreadCount = newUnread,
            unread = newUnread,
            updatedAt = lastMsg.createdAt
        )

        publishConversations(currentConvs.map { if (it.id == conversationId) updated else it })
        // A message arriving means the sender stopped typing.
        if (isIncoming) setTyping(conversationId, false)
    }

    /** Coalesces the "unknown conversation" refreshes a burst of frames would trigger. */
    private fun scheduleListRefresh() {
        if (listRefreshJob?.isActive == true) return
        listRefreshJob = scope.launch {
            delay(LIST_REFRESH_DEBOUNCE_MS)
            refreshConversations()
        }
    }

    private fun getCurrentIsoTimestamp(): String = ChatTime.nowIso()

    companion object {
        private const val TAG = "ChatRepositoryImpl"
        private const val RETRY_DELAY_MS = 2500L
        private const val TYPING_EXPIRY_MS = 4000L
        private const val LIST_REFRESH_DEBOUNCE_MS = 400L
    }
}
