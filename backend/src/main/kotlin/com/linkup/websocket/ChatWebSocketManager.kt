package com.linkup.websocket

import com.linkup.model.MessageResponse
import com.linkup.model.WebSocketFrame
import com.linkup.repository.ChatRepository
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        val set = userSessions[userId]
        return set != null && set.isNotEmpty()
    }

    suspend fun onUserConnected(userId: UUID, session: DefaultWebSocketServerSession) {
        userSessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(session)
        logger.info("User connected to WebSocket: $userId (total sessions: ${userSessions[userId]?.size})")

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
        if (sessions != null) {
            sessions.remove(session)
            if (sessions.isEmpty()) {
                userSessions.remove(userId)
            }
        }
        logger.info("User disconnected from WebSocket: $userId")
    }

    suspend fun handleSendMessage(
        senderId: UUID,
        conversationId: UUID,
        textContent: String?,
        type: String = "TEXT",
        tempId: String? = null
    ): MessageResponse {
        // 1. Save message to DB (initial status for all recipients = SENT)
        val savedMsg = chatRepository.saveMessage(
            conversationId = conversationId,
            senderId = senderId,
            textContent = textContent,
            type = type
        )

        val memberIds = chatRepository.getConversationMemberIds(conversationId)
        val recipientIds = memberIds.filter { it != senderId }

        var anyDelivered = false

        // 2. Deliver to online recipients via WS
        for (recipientId in recipientIds) {
            if (isUserOnline(recipientId)) {
                // Update DB receipt status to DELIVERED
                chatRepository.updateMessageReceiptStatus(UUID.fromString(savedMsg.id), recipientId, "DELIVERED")
                anyDelivered = true

                // Send frame to recipient
                val incomingFrame = WebSocketFrame(
                    event = "MESSAGE_RECEIVED",
                    conversationId = conversationId.toString(),
                    message = savedMsg.copy(status = "DELIVERED"),
                    tempId = tempId
                )
                broadcastToUser(recipientId, incomingFrame)
            }
        }

        val finalStatus = if (anyDelivered) "DELIVERED" else "SENT"
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
        val otherMemberIds = memberIds.filter { it != userId }

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
        val sessions = userSessions[userId] ?: return
        scope.launch {
            for (session in sessions) {
                sendToSession(session, frame)
            }
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

    private suspend fun sendToSession(session: DefaultWebSocketServerSession, frame: WebSocketFrame) {
        try {
            val jsonText = json.encodeToString(frame)
            session.send(Frame.Text(jsonText))
        } catch (e: Exception) {
            logger.error("Error sending WS frame to session", e)
        }
    }
}
