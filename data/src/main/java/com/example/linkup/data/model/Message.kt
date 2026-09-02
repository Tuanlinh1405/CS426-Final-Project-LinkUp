package com.example.linkup.data.model

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
            time = formatTime(createdAt),
            status = status.name
        )
    }

    private fun formatTime(rawIso: String): String {
        if (rawIso.length >= 16 && rawIso.contains("T")) {
            return rawIso.substringAfter("T").take(5)
        }
        return rawIso.ifEmpty { "Now" }
    }
}
