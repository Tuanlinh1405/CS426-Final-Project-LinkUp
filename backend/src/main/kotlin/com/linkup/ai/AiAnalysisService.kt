package com.linkup.ai

import com.linkup.config.EnvConfig
import com.linkup.posts.PostDto
import com.linkup.reels.ReelStorageRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Runs image analysis outside the HTTP request and fans duplicate work out to every waiting chat. */
class AiAnalysisService(
    private val repository: AiRepository,
    private val gemini: GeminiClient,
    private val storage: ReelStorageRegistry = ReelStorageRegistry(),
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(AiAnalysisService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloads = Semaphore(2)
    private val inFlight = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<String>>()

    fun fingerprint(post: PostDto): String {
        val canonical = buildString {
            append(CACHE_VERSION).append('|').append(EnvConfig.GEMINI_MODEL).append('|')
            append(post.id).append('|').append(post.updatedAt).append('|')
            append(post.author.name).append('|').append(post.author.username).append('|').append(post.content)
            post.media.forEach { media ->
                append('|').append(media.id).append(':').append(media.mimeType).append(':').append(media.storageKey)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun enqueue(fingerprint: String, post: PostDto, conversationId: UUID) {
        val shared = inFlight.computeIfAbsent(fingerprint) {
            scope.async { analyzeAndCache(fingerprint, post) }
        }
        scope.launch {
            try {
                repository.appendAnalysisResult(conversationId, shared.await())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                logger.warn("AI analysis failed for post {}: {}", post.id, error.message)
                runCatching {
                    repository.appendAnalysisResult(
                        conversationId,
                        "Mình chưa thể phân tích bài viết này lúc này. Bạn hãy thử lại sau một chút.",
                    )
                }
            } finally {
                if (shared.isCompleted) inFlight.remove(fingerprint, shared)
            }
        }
    }

    private suspend fun analyzeAndCache(fingerprint: String, post: PostDto): String {
        repository.cachedAnalysis(fingerprint)?.let { return it }
        val images = loadOptimizedImages(post)
        val answer = gemini.analyzePost(postAnalysisPrompt(post, images.size), images)
        repository.cacheAnalysis(fingerprint, answer)
        return answer
    }

    private suspend fun loadOptimizedImages(post: PostDto): List<GeminiImage> {
        val store = storage.current()
        val originals = coroutineScope {
            post.media.take(MAX_IMAGES).map { media ->
                async(Dispatchers.IO) {
                    downloads.withPermit {
                        val key = media.storageKey ?: return@withPermit null
                        runCatching {
                            val bytes = store.open(key).use { it.readNBytes(MAX_SOURCE_IMAGE_BYTES + 1) }
                            bytes.takeIf { it.isNotEmpty() && it.size <= MAX_SOURCE_IMAGE_BYTES }
                                ?.let { GeminiImage(media.mimeType, it) }
                        }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
        return withContext(Dispatchers.Default) {
            var total = 0
            originals.mapNotNull { original ->
                val optimized = AiImageOptimizer.optimize(original) ?: return@mapNotNull null
                if (total + optimized.bytes.size > MAX_TOTAL_IMAGE_BYTES) return@mapNotNull null
                total += optimized.bytes.size
                optimized
            }
        }
    }

    override fun close() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun postAnalysisPrompt(post: PostDto, imageCount: Int): String = """
        Bạn là LinkUp AI, trợ lý phân tích nội dung mạng xã hội. Trả lời bằng tiếng Việt, rõ ràng,
        hữu ích, tối đa khoảng 500 từ. Phân tích caption và $imageCount ảnh đính kèm.

        Nếu ảnh là bàn cờ/cờ thế, chỉ nhận diện quân và tọa độ khi đủ rõ; phân tích nước đi, chiến
        thuật và biến chính, đồng thời nói rõ phần không chắc chắn. Không được bịa vị trí. Với ảnh
        thông thường, giải thích chủ thể, bối cảnh, chi tiết đáng chú ý và mối liên hệ với caption.
        Với nội dung kiến thức, nêu ý chính và đánh dấu thông tin nào cần kiểm chứng. Không nhận diện
        danh tính người lạ và không suy đoán thuộc tính nhạy cảm.

        Tác giả: ${post.author.name}
        Caption: ${post.content.ifBlank { "(Không có caption; tập trung phân tích hình ảnh.)" }}
    """.trimIndent()

    companion object {
        private const val CACHE_VERSION = "post-analysis-v2-low-1600"
        private const val MAX_IMAGES = 4
        private const val MAX_SOURCE_IMAGE_BYTES = 10 * 1024 * 1024
        private const val MAX_TOTAL_IMAGE_BYTES = 5 * 1024 * 1024
    }
}
