package com.example.linkup.data.remote.api

import com.example.linkup.data.remote.dto.ConversationDto
import com.example.linkup.data.remote.dto.CreateDirectConversationRequest
import com.example.linkup.data.remote.dto.CreateGroupConversationRequest
import com.example.linkup.data.remote.dto.MediaUploadResponseDto
import com.example.linkup.data.remote.dto.MessageDto
import com.example.linkup.data.remote.dto.PresenceResponseDto
import com.example.linkup.data.remote.dto.SendMessageRequest
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApiService {

    @GET("conversations")
    suspend fun getConversations(): Response<List<ConversationDto>>

    @POST("conversations/direct")
    suspend fun createDirectConversation(
        @Body request: CreateDirectConversationRequest
    ): Response<ConversationDto>

    @POST("conversations/group")
    suspend fun createGroupConversation(
        @Body request: CreateGroupConversationRequest
    ): Response<ConversationDto>

    @GET("conversations/{id}/messages")
    suspend fun getMessages(
        @Path("id") conversationId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Long = 0
    ): Response<List<MessageDto>>

    @POST("conversations/{id}/messages")
    suspend fun sendMessage(
        @Path("id") conversationId: String,
        @Body request: SendMessageRequest
    ): Response<MessageDto>

    @POST("conversations/{id}/read")
    suspend fun markAsRead(
        @Path("id") conversationId: String
    ): Response<Map<String, String>>

    @Multipart
    @POST("conversations/{id}/media")
    suspend fun uploadChatMedia(
        @Path("id") conversationId: String,
        @Part file: MultipartBody.Part,
    ): Response<MediaUploadResponseDto>

    @GET("conversations/{id}/presence")
    suspend fun getPresence(
        @Path("id") conversationId: String,
    ): Response<PresenceResponseDto>

    @DELETE("conversations/{id}/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("id") conversationId: String,
        @Path("messageId") messageId: String,
    ): Response<Map<String, String>>
}
