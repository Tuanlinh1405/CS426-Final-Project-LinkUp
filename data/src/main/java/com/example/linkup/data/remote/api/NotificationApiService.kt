package com.example.linkup.data.remote.api

import com.example.linkup.data.remote.dto.NotificationBulkResultDto
import com.example.linkup.data.remote.dto.NotificationPageDto
import com.example.linkup.data.remote.dto.UnreadCountDto
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApiService {

    @GET("notifications")
    suspend fun list(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("filter") filter: String? = null
    ): Response<NotificationPageDto>

    @GET("notifications/unread-count")
    suspend fun unreadCount(): Response<UnreadCountDto>

    @PUT("notifications/{id}/read")
    suspend fun setRead(
        @Path("id") id: String,
        @Query("read") read: Boolean
    ): Response<NotificationBulkResultDto>

    @PUT("notifications/read-all")
    suspend fun markAllRead(): Response<NotificationBulkResultDto>

    @DELETE("notifications/{id}")
    suspend fun delete(@Path("id") id: String): Response<NotificationBulkResultDto>

    @DELETE("notifications")
    suspend fun clearAll(): Response<NotificationBulkResultDto>
}
