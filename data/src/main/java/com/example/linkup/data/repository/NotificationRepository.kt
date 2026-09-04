package com.example.linkup.data.repository

import com.example.linkup.data.model.NotificationPage
import kotlinx.coroutines.flow.SharedFlow

data class NotificationRealtimeUpdate(
    val unreadCount: Int?
)

interface NotificationRepository {

    val realtimeUpdates: SharedFlow<NotificationRealtimeUpdate>

    /** One page, newest first. Pass the previous page's cursor to continue. */
    suspend fun load(cursor: String?, unreadOnly: Boolean): Result<NotificationPage>

    suspend fun unreadCount(): Result<Int>

    /** @return the remaining unread count. */
    suspend fun setRead(id: String, read: Boolean): Result<Int>

    suspend fun markAllRead(): Result<Int>

    suspend fun delete(id: String): Result<Int>

    suspend fun clearAll(): Result<Int>
}
