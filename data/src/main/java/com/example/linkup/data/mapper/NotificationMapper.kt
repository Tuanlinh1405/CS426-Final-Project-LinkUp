package com.example.linkup.data.mapper

import com.example.linkup.data.model.Notification
import com.example.linkup.data.model.NotificationActor
import com.example.linkup.data.model.NotificationPage
import com.example.linkup.data.model.NotificationType
import com.example.linkup.data.remote.dto.NotificationActorDto
import com.example.linkup.data.remote.dto.NotificationDto
import com.example.linkup.data.remote.dto.NotificationPageDto

fun NotificationActorDto.toDomain(): NotificationActor = NotificationActor(
    id = id,
    username = username,
    fullName = fullName,
    avatarUrl = avatarUrl
)

fun NotificationDto.toDomain(): Notification = Notification(
    id = id,
    type = NotificationType.from(type),
    actor = actor.toDomain(),
    targetId = targetId,
    isRead = isRead,
    createdAt = createdAt
)

fun NotificationPageDto.toDomain(): NotificationPage = NotificationPage(
    items = items.map { it.toDomain() },
    nextCursor = nextCursor,
    unreadCount = unreadCount
)
