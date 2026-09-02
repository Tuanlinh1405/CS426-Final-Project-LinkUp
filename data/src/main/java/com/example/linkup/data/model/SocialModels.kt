package com.example.linkup.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val username: String,
    val initials: String,
    val bio: String = ""
)

@Serializable
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

@Serializable
data class Reel(
    val id: String,
    val author: User,
    val caption: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String? = null,
    val likes: Int = 0,
    val comments: Int = 0,
    val liked: Boolean = false,
    val audioTitle: String = "original sound"
)

@Serializable
data class ChatMessage(
    val id: String,
    val text: String,
    val fromMe: Boolean,
    val time: String,
    val status: String = "SENT"
)

@Serializable
data class Conversation(
    val id: String,
    val user: User,
    val preview: String,
    val time: String,
    val unread: Int = 0
)

@Serializable
data class NotificationItem(
    val id: String,
    val actor: User,
    val text: String,
    val time: String,
    val unread: Boolean = true
)

