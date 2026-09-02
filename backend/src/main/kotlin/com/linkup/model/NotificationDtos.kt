package com.linkup.model

import kotlinx.serialization.Serializable

/** Who triggered a notification — enough to render a row without a second call. */
@Serializable
data class NotificationActorDto(
    val id: String,
    val username: String,
    val fullName: String? = null,
    val avatarUrl: String? = null
)

/**
 * One notification.
 *
 * [targetId] is what tapping the row should open: the actor's profile for FOLLOW,
 * a post for LIKE and COMMENT, and so on. The client decides the destination from
 * [type], so an unknown type degrades to a plain, non-navigating row.
 */
@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val actor: NotificationActorDto,
    val targetId: String? = null,
    val isRead: Boolean = false,
    val createdAt: String
)

/**
 * A page of notifications.
 *
 * [nextCursor] is null on the last page. [unreadCount] is the total across every
 * page, so the badge stays right without fetching everything.
 */
@Serializable
data class NotificationPageDto(
    val items: List<NotificationDto>,
    val nextCursor: String? = null,
    val unreadCount: Int = 0
)

@Serializable
data class UnreadCountDto(val unreadCount: Int)

/** Returned by the bulk endpoints so the client can reconcile without a refetch. */
@Serializable
data class NotificationBulkResultDto(
    val affected: Int,
    val unreadCount: Int
)
