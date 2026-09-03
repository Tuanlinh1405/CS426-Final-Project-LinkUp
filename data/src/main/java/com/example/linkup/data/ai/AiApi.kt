package com.example.linkup.data.ai

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AiApi {
    @POST("ai/posts/{postId}/analyze")
    suspend fun analyzePost(@Path("postId") postId: String): Response<AiAnalysisResponse>

    @GET("ai/conversations")
    suspend fun conversations(): Response<List<AiConversation>>

    @POST("ai/conversations")
    suspend fun createConversation(@Body request: AiCreateConversationRequest): Response<AiConversation>

    @GET("ai/conversations/{id}/messages")
    suspend fun messages(@Path("id") conversationId: String): Response<List<AiMessage>>

    @POST("ai/conversations/{id}/messages")
    suspend fun send(
        @Path("id") conversationId: String,
        @Body request: AiPromptRequest,
    ): Response<List<AiMessage>>
}
