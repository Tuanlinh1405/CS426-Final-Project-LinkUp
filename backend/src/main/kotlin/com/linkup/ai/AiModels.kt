package com.linkup.ai

import kotlinx.serialization.Serializable

@Serializable
data class AiConversationDto(
    val id: String,
    val title: String,
    val lastMessage: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class AiMessageDto(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: String,
)

@Serializable data class AiPromptRequest(val content: String)
@Serializable data class AiCreateConversationRequest(val title: String? = null)
@Serializable data class AiAnalysisResponse(val conversation: AiConversationDto, val messages: List<AiMessageDto>)
@Serializable data class AiError(val message: String)

class AiFailure(val status: Int, override val message: String) : RuntimeException(message)

data class GeminiImage(val mimeType: String, val bytes: ByteArray)
