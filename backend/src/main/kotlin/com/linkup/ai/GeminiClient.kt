package com.linkup.ai

import com.linkup.config.EnvConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

class GeminiClient(
    private val apiKey: () -> String? = { EnvConfig.GEMINI_API_KEY },
    private val model: () -> String = { EnvConfig.GEMINI_MODEL },
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun analyzePost(prompt: String, images: List<GeminiImage>): String {
        val parts = images.map { image ->
            GeminiPart(inlineData = GeminiInlineData(image.mimeType, Base64.getEncoder().encodeToString(image.bytes)))
        } + GeminiPart(text = prompt)
        return generate(listOf(GeminiContent(role = "user", parts = parts)))
    }

    suspend fun chat(history: List<AiMessageDto>, prompt: String): String {
        val contents = history.takeLast(MAX_HISTORY_MESSAGES).map { message ->
            GeminiContent(
                role = if (message.role == "model") "model" else "user",
                parts = listOf(GeminiPart(text = message.content)),
            )
        } + GeminiContent(role = "user", parts = listOf(GeminiPart(text = prompt)))
        return generate(contents)
    }

    private suspend fun generate(contents: List<GeminiContent>): String = withContext(Dispatchers.IO) {
        val key = apiKey()?.takeIf(String::isNotBlank) ?: throw AiFailure(503, "Gemini chưa được cấu hình trên backend.")
        val selectedModel = model().takeIf { it.matches(Regex("[A-Za-z0-9._-]+")) }
            ?: throw AiFailure(500, "Tên Gemini model không hợp lệ.")
        val body = json.encodeToString(
            GeminiRequest(
                contents = contents,
                generationConfig = GeminiGenerationConfig(
                    maxOutputTokens = 800,
                    thinkingConfig = GeminiThinkingConfig(thinkingLevel = "low"),
                ),
            )
        )
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent"))
            .timeout(Duration.ofSeconds(75))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", key)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            val detail = runCatching { json.decodeFromString<GeminiErrorEnvelope>(response.body()).error?.message }.getOrNull()
            throw AiFailure(if (response.statusCode() == 429) 429 else 502, detail ?: "Gemini tạm thời không phản hồi.")
        }
        val decoded = runCatching { json.decodeFromString<GeminiResponse>(response.body()) }
            .getOrElse { throw AiFailure(502, "Gemini trả về dữ liệu không hợp lệ.") }
        decoded.candidates.firstOrNull()?.content?.parts.orEmpty()
            .mapNotNull(GeminiPart::text).joinToString("\n").trim()
            .takeIf(String::isNotBlank)
            ?: throw AiFailure(502, "Gemini không tạo được nội dung phân tích.")
    }

    companion object { private const val MAX_HISTORY_MESSAGES = 20 }
}

@Serializable private data class GeminiRequest(val contents: List<GeminiContent>, val generationConfig: GeminiGenerationConfig)
@Serializable private data class GeminiContent(val role: String? = null, val parts: List<GeminiPart>)
@Serializable private data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: GeminiInlineData? = null,
)
@Serializable private data class GeminiInlineData(
    @SerialName("mime_type") val mimeType: String,
    val data: String,
)
@Serializable private data class GeminiGenerationConfig(
    val maxOutputTokens: Int,
    val thinkingConfig: GeminiThinkingConfig,
)
@Serializable private data class GeminiThinkingConfig(val thinkingLevel: String)
@Serializable private data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())
@Serializable private data class GeminiCandidate(val content: GeminiContent? = null)
@Serializable private data class GeminiErrorEnvelope(val error: GeminiErrorBody? = null)
@Serializable private data class GeminiErrorBody(val message: String? = null)
