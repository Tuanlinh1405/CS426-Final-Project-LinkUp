package com.linkup.websocket

import com.linkup.model.MessageResponse
import com.linkup.model.WebSocketFrame
import com.linkup.repository.ChatRepository
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChatWebSocketManager(private val chatRepository: ChatRepository) {
    private val logger = LoggerFactory.getLogger(ChatWebSocketManager::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(Dispatchers.IO)

    // Map userId -> set of active WebSocket sessions
    private val userSessions = ConcurrentHashMap<UUID, MutableSet<DefaultWebSocketServerSession>>()

    fun isUserOnline(userId: UUID): Boolean {
        val sessions = userSessions[userId] ?: return false
        // Drop sockets whose coroutine already died: a killed emulator leaves its session
        // registered until the ping timeout fires, and presence must not lie in that window.
        sessions.removeIf { !it.isActive }
        if (sessions.isEmpty()) {
            userSessions.remove(userId, sessions)
            return false
        }
        return true
    }

    suspend fun onUserConnected(userId: UUID, session: DefaultWebSocketServerSession) {
        val sessions = userSessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }

        // Evict stale sessions from the same user (dual-connection from reconnect).
        // Without this, a reconnect creates a second entry; the old socket lingers until
        // the ping timeout, during which sendToUserNow delivers to the dead one first.
        val stale = sessions.filter { it != session && it.isActive }
        for (old in stale) {
            try { old.close() } catch (_: Exception) {}
            sessions.remove(old)
        }

        val wasOffline = sessions.isEmpty()
        sessions.add(session)
        logger.info("User connected to WebSocket: $userId (total sessions: ${sessions.size})")

        // Watch for an abnormal drop (emulator killed / network cut). The main incoming
        // loop only calls onUserDisconnected when the for-loop ends, which can be delayed
        // by the 30s ping timeout; without this, a stale session keeps isUserOnline == true
        // and messages get wrongly marked DELIVERED.
        monitorDisconnect(userId, session)

        if (wasOffline) broadcastPresence(userId, online = true)

        // Auto-flush pending messages for this user (messages sent while user was offline)
        try {
            val pendingMessages = chatRepository.getPendingMessagesForUser(userId)
            for ((convId, msg) in pendingMessages) {
                // Update receipt to DELIVERED in DB
                chatRepository.updateMessageReceiptStatus(UUID.fromString(msg.id), userId, "DELIVERED")

                // Deliver to user via WS
                val incomingFrame = WebSocketFrame(
                    event = "MESSAGE_RECEIVED",
                    conversationId = convId.toString(),
                    message = msg.copy(status = "DELIVERED")
                )
                sendToSession(session, incomingFrame)

                // Notify original sender that message was delivered
                val senderId = UUID.fromString(msg.senderId)
                val statusFrame = WebSocketFrame(
                    event = "MESSAGE_STATUS_UPDATE",
                    conversationId = convId.toString(),
                    messageId = msg.id,
                    status = "DELIVERED"
                )
                broadcastToUser(senderId, statusFrame)
            }
        } catch (e: Exception) {
            logger.error("Error flushing pending messages for user $userId", e)
        }
    }

    fun onUserDisconnected(userId: UUID, session: DefaultWebSocketServerSession) {
        val sessions = userSessions[userId]
        // remove() tells us whether this call is the one that actually evicted the session:
        // both the route's finally block and the job-completion hook fire for the same socket,
        // and only one of them may announce OFFLINE.
        val evicted = sessions?.remove(session) == true
        if (sessions != null && sessions.isEmpty()) {
            userSessions.remove(userId, sessions)
        }
        logger.info("User disconnected from WebSocket: $userId")

        if (evicted && !isUserOnline(userId)) broadcastPresence(userId, online = false)
    }

    /**
     * Tells everyone who shares a conversation with [userId] that they came online or went
     * offline. Only transitions are announced; a client opening a thread reads the current
     * state from `GET /conversations/{id}/presence`.
     */
    private fun broadcastPresence(userId: UUID, online: Boolean) {
        val frame = WebSocketFrame(
            event = "PRESENCE",
            senderId = userId.toString(),
            status = if (online) "ONLINE" else "OFFLINE"
        )
        scope.launch {
            try {
                for (peerId in chatRepository.getPeerUserIds(userId)) {
                    sendToUserNow(peerId, frame)
                }
            } catch (e: Exception) {
                logger.error("Error broadcasting presence for $userId", e)
            }
        }
    }

    /**
     * Ktor completes a session's coroutine job as soon as the socket dies, which happens
     * well before the reading loop in the route returns. Hooking the job gives us prompt
     * eviction so presence (and therefore DELIVERED vs SENT) stays truthful.
     */
    private fun monitorDisconnect(userId: UUID, session: DefaultWebSocketServerSession) {
        session.coroutineContext[Job]?.invokeOnCompletion {
            onUserDisconnected(userId, session)
        }
    }

    suspend fun handleSendMessage(
        senderId: UUID,
        conversationId: UUID,
        textContent: String?,
        type: String = "TEXT",
        tempId: String? = null,
        mediaUrl: String? = null,
        sharedContentId: UUID? = null
    ): MessageResponse? {
        // Sender must be a member; otherwise any user could write into any conversation.
        if (!chatRepository.isConversationMember(conversationId, senderId)) {
            logger.warn("Rejected send by non-member $senderId to conversation $conversationId")
            return null
        }

        // 1. Save message to DB (initial status for all recipients = SENT)
        val savedMsg = chatRepository.saveMessage(
            conversationId = conversationId,
            senderId = senderId,
            textContent = textContent,
            type = type,
            mediaUrl = mediaUrl,
            sharedContentId = sharedContentId
        )

        val memberIds = chatRepository.getConversationMemberIds(conversationId)
        val recipientIds = memberIds.filter { it != senderId }

        // 2. Deliver first, then flip receipts for whoever actually accepted the frame.
        // Asking a presence map instead would report a killed client as online until its
        // ping times out (~30s), and the sender would see a false double tick.
        val deliveredTo = recipientIds.filter { recipientId ->
            val incomingFrame = WebSocketFrame(
                event = "MESSAGE_RECEIVED",
                conversationId = conversationId.toString(),
                message = savedMsg.copy(status = "DELIVERED"),
                tempId = tempId
            )
            sendToUserNow(recipientId, incomingFrame)
        }
        if (deliveredTo.isNotEmpty()) {
            chatRepository.markDeliveredForRecipients(UUID.fromString(savedMsg.id), deliveredTo)
        }

        val finalStatus = if (deliveredTo.isNotEmpty()) "DELIVERED" else "SENT"
        val responseMsg = savedMsg.copy(status = finalStatus)

        // 3. Send ACK back to sender with final status
        val ackFrame = WebSocketFrame(
            event = "MESSAGE_STATUS_UPDATE",
            conversationId = conversationId.toString(),
            messageId = savedMsg.id,
            message = responseMsg,
            status = finalStatus,
            tempId = tempId
        )
        broadcastToUser(senderId, ackFrame)

        return responseMsg
    }

    suspend fun handleMarkRead(userId: UUID, conversationId: UUID) {
        val markedMsgIds = chatRepository.markConversationAsRead(conversationId, userId)
        if (markedMsgIds.isEmpty()) return

        val memberIds = chatRepository.getConversationMemberIds(conversationId)
        val otherMemberIds = memberIds.filter { it != userId }.toSet()

        // One frame per affected message; the sender of the seal already gets a copy.
        for (msgId in markedMsgIds) {
            val statusFrame = WebSocketFrame(
                event = "MESSAGE_STATUS_UPDATE",
                conversationId = conversationId.toString(),
                messageId = msgId.toString(),
                senderId = userId.toString(),
                status = "SEEN"
            )
            for (memberId in otherMemberIds) {
                broadcastToUser(memberId, statusFrame)
            }
        }
    }

    fun broadcastToUser(userId: UUID, frame: WebSocketFrame) {
        scope.launch { sendToUserNow(userId, frame) }
    }

    /**
     * Sends to every session of [userId] and evicts the ones that are already dead or that
     * fail mid-send (network cut, killed emulator). Presence therefore stops lying before
     * the 30s ping timeout, which is what made a message to an offline peer show DELIVERED.
     *
     * @return true if at least one session accepted the frame.
     */
    private suspend fun sendToUserNow(userId: UUID, frame: WebSocketFrame): Boolean {
        val sessions = userSessions[userId] ?: return false
        var delivered = false
        for (session in sessions) {
            if (sendToSession(session, frame)) delivered = true else onUserDisconnected(userId, session)
        }
        return delivered
    }

    /**
     * Tells members other than [excludeUserId] that a conversation now exists for them.
     *
     * Without this, being added to a group is invisible until the app is restarted or the
     * list is pulled manually — the client has no row to attach an incoming message to.
     */
    fun notifyConversationCreated(conversationId: UUID, memberIds: List<UUID>, excludeUserId: UUID? = null) {
        val frame = WebSocketFrame(
            event = "CONVERSATION_CREATED",
            conversationId = conversationId.toString()
        )
        for (memberId in memberIds) {
            if (memberId != excludeUserId) broadcastToUser(memberId, frame)
        }
    }

    fun broadcastToConversation(conversationId: UUID, frame: WebSocketFrame, excludeUserId: UUID? = null) {
        scope.launch {
            val memberIds = chatRepository.getConversationMemberIds(conversationId)
            for (memberId in memberIds) {
                if (excludeUserId == null || memberId != excludeUserId) {
                    broadcastToUser(memberId, frame)
                }
            }
        }
    }

    /**
     * Tells every member (including the deleter) that a message is gone. The client drops
     * the bubble from its list; no tombstone row is sent.
     */
    fun handleDeleteMessage(conversationId: UUID, messageId: UUID) {
        val frame = WebSocketFrame(
            event = "MESSAGE_DELETED",
            conversationId = conversationId.toString(),
            messageId = messageId.toString()
        )
        broadcastToConversation(conversationId, frame)
    }

    /** Returns false when the socket is already dead, so the caller can evict the session. */
    private suspend fun sendToSession(session: DefaultWebSocketServerSession, frame: WebSocketFrame): Boolean {
        if (!session.isActive) return false
        return try {
            val jsonText = json.encodeToString(frame)
            session.send(Frame.Text(jsonText))
            true
        } catch (e: Exception) {
            logger.error("Error sending WS frame to session", e)
            false
        }
    }
}
