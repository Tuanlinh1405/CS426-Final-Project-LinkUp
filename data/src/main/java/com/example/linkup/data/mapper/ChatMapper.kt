package com.example.linkup.data.mapper

import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.Message
import com.example.linkup.data.model.MessageStatus
import com.example.linkup.data.model.Participant
import com.example.linkup.data.model.User
import com.example.linkup.data.remote.dto.ConversationDto
import com.example.linkup.data.remote.dto.MessageDto
import com.example.linkup.data.remote.dto.ParticipantDto

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

    val otherParticipant = domainParticipants.firstOrNull { it.id != currentUserId }
        ?: domainParticipants.firstOrNull()

    val title = if (type == "GROUP") {
        name ?: "Group Chat"
    } else {
        otherParticipant?.displayName ?: name ?: "Chat"
    }

    val displayUser = User(
        id = otherParticipant?.id ?: id,
        name = title,
        username = otherParticipant?.username?.let { "@$it" } ?: "@chat",
        initials = otherParticipant?.initials ?: "C"
    )

    val formattedTime = domainLastMessage?.createdAt?.let { rawIso ->
        if (rawIso.length >= 16 && rawIso.contains("T")) {
            rawIso.substringAfter("T").take(5)
        } else {
            rawIso
        }
    } ?: if (updatedAt.length >= 16 && updatedAt.contains("T")) {
        updatedAt.substringAfter("T").take(5)
    } else {
        updatedAt.ifEmpty { "Now" }
    }

    return Conversation(
        id = id,
        type = type,
        name = name,
        participants = domainParticipants,
        lastMessage = domainLastMessage,
        unreadCount = unreadCount,
        updatedAt = updatedAt,
        user = displayUser,
        preview = domainLastMessage?.textContent ?: "No messages yet",
        time = formattedTime,
        unread = unreadCount
    )
}
