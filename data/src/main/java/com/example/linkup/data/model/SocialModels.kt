package com.example.linkup.data.model

data class User(
    val id: String,
    val name: String,
    val username: String,
    val initials: String,
    val bio: String = ""
)

data class Post(
    val id: String,
    val author: User,
    val time: String,
    val content: String,
    val mediaLabel: String? = null,
    val likes: Int = 0,
    val comments: Int = 0,
    val liked: Boolean = false
)

data class ChatMessage(
    val id: String,
    val text: String,
    val fromMe: Boolean,
    val time: String,
    val status: String = "SENT"
)

data class Conversation(
    val id: String,
    val user: User = User(id, "User", "@user", "U"),
    val preview: String = "",
    val time: String = "",
    val unread: Int = 0,
    val type: String = "DIRECT",
    val name: String? = null,
    val participants: List<Participant> = emptyList(),
    val lastMessage: Message? = null,
    val unreadCount: Int = unread,
    val updatedAt: String = time,
)

data class NotificationItem(
    val id: String,
    val actor: User,
    val text: String,
    val time: String,
    val unread: Boolean = true
)
