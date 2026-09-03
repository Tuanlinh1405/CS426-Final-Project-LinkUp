package com.example.linkup.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ParticipantDto(
    val id: String,
    val username: String,
    val fullName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class MessageDto(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String? = null,
    val type: String = "TEXT",
    val textContent: String? = null,
    val mediaUrl: String? = null,
    val sharedContentId: String? = null,
    val status: String = "SENT",
    val createdAt: String = "",
)

@Serializable
data class ConversationDto(
    val id: String,
    val type: String = "DIRECT",
    val name: String? = null,
    val participants: List<ParticipantDto> = emptyList(),
    val lastMessage: MessageDto? = null,
    val unreadCount: Int = 0,
    val updatedAt: String = "",
)

@Serializable
data class WebSocketFrameDto(
    val event: String,
    val conversationId: String? = null,
    val message: MessageDto? = null,
    val messageId: String? = null,
    val senderId: String? = null,
    val status: String? = null,
    val tempId: String? = null,
    val isTyping: Boolean? = null,
    val error: String? = null,
)

@Serializable
data class CreateDirectConversationRequest(
    val targetUserId: String,
)

@Serializable
data class CreateGroupConversationRequest(
    val name: String,
    val memberUserIds: List<String>,
)

@Serializable
data class SendMessageRequest(
    val textContent: String? = null,
    val type: String = "TEXT",
    val mediaUrl: String? = null,
    val sharedContentId: String? = null,
    val tempId: String? = null,
)

@Serializable
data class MarkReadRequest(
    val conversationId: String,
    val messageId: String? = null,
)

/** Members of a conversation with a live socket. PRESENCE frames only carry transitions. */
@Serializable
data class PresenceResponseDto(
    val onlineUserIds: List<String> = emptyList(),
)
