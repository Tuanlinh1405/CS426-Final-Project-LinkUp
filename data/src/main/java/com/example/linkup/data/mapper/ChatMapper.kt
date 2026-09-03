package com.example.linkup.data.mapper

import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.Message
import com.example.linkup.data.model.MessageStatus
import com.example.linkup.data.model.Participant
import com.example.linkup.data.model.User
import com.example.linkup.data.remote.dto.ConversationDto
import com.example.linkup.data.remote.dto.MessageDto
import com.example.linkup.data.remote.dto.ParticipantDto
import com.example.linkup.data.util.ChatTime

fun ParticipantDto.toDomain(): Participant {
    return Participant(
        id = id,
        username = username,
        fullName = fullName,
        avatarUrl = avatarUrl
    )
}

fun MessageDto.toDomain(currentUserId: String? = null): Message {
    return Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        type = type,
        textContent = textContent,
        mediaUrl = mediaUrl,
        status = MessageStatus.fromString(status),
        createdAt = createdAt,
        fromMe = currentUserId != null && senderId == currentUserId
    )
}

fun ConversationDto.toDomain(currentUserId: String? = null): Conversation {
    val domainParticipants = participants.map { it.toDomain() }
    val domainLastMessage = lastMessage?.toDomain(currentUserId)

    val others = domainParticipants.filter { it.id != currentUserId }
    val otherParticipant = others.firstOrNull() ?: domainParticipants.firstOrNull()

    val isGroup = type == "GROUP"
    val title = when {
        isGroup -> name?.takeIf { it.isNotBlank() }
            ?: others.take(3).joinToString(", ") { it.displayName }.ifBlank { "Group Chat" }
        else -> otherParticipant?.displayName ?: name ?: "Chat"
    }

    val displayUser = User(
        id = otherParticipant?.id ?: id,
        name = title,
        username = if (isGroup) {
            "${domainParticipants.size} thành viên"
        } else {
            otherParticipant?.username?.let { "@$it" } ?: "@chat"
        },
        initials = if (isGroup) groupInitials(title) else otherParticipant?.initials ?: "C",
        avatarUrl = if (isGroup) null else otherParticipant?.avatarUrl
    )

    val preview = buildPreview(isGroup, domainLastMessage, others)

    return Conversation(
        id = id,
        type = type,
        name = name,
        participants = domainParticipants,
        lastMessage = domainLastMessage,
        unreadCount = unreadCount,
        updatedAt = domainLastMessage?.createdAt?.takeIf { it.isNotBlank() } ?: updatedAt,
        user = displayUser,
        preview = preview,
        time = ChatTime.listStamp(domainLastMessage?.createdAt?.takeIf { it.isNotBlank() } ?: updatedAt),
        unread = unreadCount,
        others = others
    )
}

private fun groupInitials(title: String): String = title
    .split(' ', ',', '.', '_')
    .filter { it.isNotBlank() }
    .take(2)
    .map { it.first().uppercaseChar() }
    .joinToString("")
    .ifEmpty { "G" }

/**
 * The one-line summary under a conversation title.
 *
 * Group previews carry the speaker's name, because otherwise a group of five reads as
 * an anonymous stream. Shared with the realtime path so a WebSocket update produces
 * exactly the same text as a fresh fetch.
 */
fun buildPreview(
    isGroup: Boolean,
    lastMessage: Message?,
    others: List<Participant>,
): String {
    if (lastMessage == null) return "No messages yet"
    val body = when {
        lastMessage.type == "IMAGE" -> lastMessage.textContent?.takeIf { it.isNotBlank() } ?: "📷 Ảnh"
        else -> lastMessage.textContent ?: "Media message"
    }
    if (!isGroup) return body

    val speaker = if (lastMessage.fromMe) {
        "Bạn"
    } else {
        lastMessage.senderName ?: others.firstOrNull { it.id == lastMessage.senderId }?.displayName
    }
    return if (speaker == null) body else "$speaker: $body"
}
