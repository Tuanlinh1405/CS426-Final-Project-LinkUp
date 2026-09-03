package com.example.linkup.feature.more.notifications

import com.example.linkup.data.model.Notification

enum class NotificationFilter { ALL, UNREAD }

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val items: List<Notification> = emptyList(),
    val unreadCount: Int = 0,
    val filter: NotificationFilter = NotificationFilter.ALL,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val nextCursor: String? = null,
    /** Rows with an in-flight write, so their controls can be disabled individually. */
    val busyIds: Set<String> = emptySet(),
    /** Transient feedback shown as a dismissible strip. */
    val message: String? = null,
    val messageIsError: Boolean = false
) {
    val hasMore: Boolean get() = nextCursor != null

    val isEmpty: Boolean get() = items.isEmpty() && !isLoading && error == null

    val canMarkAllRead: Boolean get() = unreadCount > 0 && !isLoading
}
