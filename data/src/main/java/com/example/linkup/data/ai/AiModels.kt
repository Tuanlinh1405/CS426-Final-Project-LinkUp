package com.example.linkup.data.ai

import kotlinx.serialization.Serializable

@Serializable data class AiConversation(
    val id: String,
    val title: String,
    val lastMessage: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable data class AiMessage(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: String,
)

@Serializable data class AiPromptRequest(val content: String)
@Serializable data class AiCreateConversationRequest(val title: String? = null)
@Serializable data class AiAnalysisResponse(val conversation: AiConversation, val messages: List<AiMessage>)
@Serializable data class AiApiError(val message: String)

class AiApiException(val status: Int, message: String) : Exception(message)
