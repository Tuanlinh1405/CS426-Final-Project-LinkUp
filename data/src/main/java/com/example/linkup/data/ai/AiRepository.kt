package com.example.linkup.data.ai

import com.example.linkup.data.network.ApiClient
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

interface AiRepository {
    suspend fun analyzePost(postId: String): Result<AiAnalysisResponse>
    suspend fun conversations(): Result<List<AiConversation>>
    suspend fun createConversation(title: String? = null): Result<AiConversation>
    suspend fun messages(conversationId: String): Result<List<AiMessage>>
    suspend fun send(conversationId: String, prompt: String): Result<List<AiMessage>>
}

@Singleton
class AiRepositoryImpl @Inject constructor(private val api: AiApi) : AiRepository {
    override suspend fun analyzePost(postId: String) = request { api.analyzePost(postId) }
    override suspend fun conversations() = request { api.conversations() }
    override suspend fun createConversation(title: String?) = request { api.createConversation(AiCreateConversationRequest(title)) }
    override suspend fun messages(conversationId: String) = request { api.messages(conversationId) }
    override suspend fun send(conversationId: String, prompt: String) = request { api.send(conversationId, AiPromptRequest(prompt)) }

    private suspend fun <T> request(call: suspend () -> Response<T>): Result<T> = try {
        val response = call()
        if (!response.isSuccessful) {
            val message = response.errorBody()?.use { body ->
                runCatching { ApiClient.json.decodeFromString<AiApiError>(body.string()).message }.getOrNull()
            } ?: "LinkUp AI gặp lỗi (${response.code()})."
            throw AiApiException(response.code(), message)
        }
        Result.success(response.body() ?: throw AiApiException(502, "Backend không trả về nội dung AI."))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
