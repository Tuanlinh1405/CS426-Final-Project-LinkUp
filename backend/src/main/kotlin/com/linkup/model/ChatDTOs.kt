package com.linkup.model

import kotlinx.serialization.Serializable

@Serializable
data class ParticipantDto(
    val id: String,
    val username: String,
    val fullName: String?,
    val avatarUrl: String? = null
)

@Serializable
data class ConversationResponse(
    val id: String,
    val type: String,
    val name: String? = null,
    val participants: List<ParticipantDto>,
    val lastMessage: MessageResponse? = null,
    val unreadCount: Int = 0,
    val updatedAt: String
)

@Serializable
data class MessageResponse(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String? = null,
    val type: String = "TEXT",
    val textContent: String? = null,
    val mediaUrl: String? = null,
    val status: String = "SENT",
    val createdAt: String = ""
)

@Serializable
data class CreateDirectConversationRequest(
    val targetUserId: String
)

@Serializable
data class CreateGroupConversationRequest(
    val name: String,
    val memberUserIds: List<String>
)

@Serializable
data class SendMessageRequest(
    val textContent: String? = null,
    val type: String = "TEXT",
    val mediaUrl: String? = null,
    val tempId: String? = null
)

@Serializable
data class MarkReadRequest(
    val conversationId: String,
    val messageId: String? = null
)

/**
 * Who in a conversation has a live WebSocket right now.
 *
 * The PRESENCE frames only announce transitions, so a client that opens a thread needs
 * this to know the current state.
 */
@Serializable
data class PresenceResponse(
    val onlineUserIds: List<String> = emptyList()
)

@Serializable
data class WebSocketFrame(
    val event: String,
    val conversationId: String? = null,
    val message: MessageResponse? = null,
    val messageId: String? = null,
    val senderId: String? = null,
    val status: String? = null,
    val tempId: String? = null,
    val isTyping: Boolean? = null,
    val error: String? = null
)
