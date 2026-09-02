package com.example.linkup.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationActorDto(
    val id: String,
    val username: String,
    val fullName: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val actor: NotificationActorDto,
    val targetId: String? = null,
    val isRead: Boolean = false,
    val createdAt: String = ""
)

@Serializable
data class NotificationPageDto(
    val items: List<NotificationDto> = emptyList(),
    val nextCursor: String? = null,
    val unreadCount: Int = 0
)

@Serializable
data class UnreadCountDto(val unreadCount: Int = 0)

@Serializable
data class NotificationBulkResultDto(
    val affected: Int = 0,
    val unreadCount: Int = 0
)
