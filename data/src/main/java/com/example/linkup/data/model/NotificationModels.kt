package com.example.linkup.data.model

/**
 * Notification kinds the client understands.
 *
 * [UNKNOWN] keeps the app forward compatible: a type added by the backend later
 * still renders as a readable row instead of crashing or vanishing.
 */
enum class NotificationType {
    FOLLOW,
    LIKE,
    COMMENT,
    MENTION,
    MESSAGE,
    FRIEND_REQUEST,
    FRIEND_ACCEPT,
    DATING_MATCH,
    SYSTEM,
    UNKNOWN;

    companion object {
        fun from(raw: String): NotificationType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

data class NotificationActor(
    val id: String,
    val username: String,
    val fullName: String?,
    val avatarUrl: String?
) {
    val displayName: String get() = fullName?.takeIf { it.isNotBlank() } ?: username

    val initials: String
        get() = displayName
            .split(' ', '.', '_')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { username.take(1).uppercase() }
}

data class Notification(
    val id: String,
    val type: NotificationType,
    val actor: NotificationActor,
    val targetId: String?,
    val isRead: Boolean,
    val createdAt: String
) {
    /**
     * A system notice is addressed to the user by the app itself; the actor column
     * only holds the recipient to satisfy the foreign key, so it must not be shown
     * as "You did something to yourself".
     */
    val isSystem: Boolean get() = type == NotificationType.SYSTEM

    /** What tapping this row should open, or null when it goes nowhere. */
    val destinationUserId: String?
        get() = when (type) {
            // All three lead to the person who acted; the profile is where you
            // accept a request or follow back.
            NotificationType.FOLLOW,
            NotificationType.FRIEND_REQUEST,
            NotificationType.FRIEND_ACCEPT -> targetId ?: actor.id
            else -> null
        }
}

data class NotificationPage(
    val items: List<Notification>,
    val nextCursor: String?,
    val unreadCount: Int
)
