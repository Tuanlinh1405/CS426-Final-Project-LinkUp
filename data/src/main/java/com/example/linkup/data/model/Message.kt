package com.example.linkup.data.model

import com.example.linkup.data.util.ChatTime

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String? = null,
    val type: String = "TEXT",
    val textContent: String? = null,
    val mediaUrl: String? = null,
    val status: MessageStatus = MessageStatus.SENT,
    val createdAt: String = "",
    val fromMe: Boolean = false,
) {
    fun toChatMessage(): ChatMessage {
        return ChatMessage(
            id = id,
            text = textContent ?: "",
            fromMe = fromMe,
            time = ChatTime.clock(createdAt),
            status = status.name
        )
    }
}
