package com.example.linkup.data.feed

import com.example.linkup.data.network.ApiClient
import com.example.linkup.data.network.AuthSession
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import retrofit2.Response
import java.io.File
import java.util.concurrent.ConcurrentHashMap

interface PostRepository {
    suspend fun feed(cursor: String? = null): FeedPage
    suspend fun get(id: String): FeedPost
    suspend fun create(id: String, content: String, images: List<File>, progress: (Float) -> Unit): FeedPost
    suspend fun like(id: String, liked: Boolean): FeedPost
    suspend fun delete(id: String)
    suspend fun comments(id: String, cursor: String? = null): FeedCommentPage
    fun cachedComments(id: String): FeedCommentPage? = null
    suspend fun prefetchComments(id: String) { comments(id) }
    suspend fun comment(id: String, body: AddFeedComment): FeedComment
    suspend fun likeComment(id: String, commentId: String, liked: Boolean): FeedComment
    suspend fun deleteComment(id: String, commentId: String)
}

class PostRepositoryImpl(private val api: PostApi = ApiClient.retrofit.create(PostApi::class.java)) : PostRepository {
    private data class CachedComments(val page: FeedCommentPage, val storedAt: Long)
    private val commentCache = ConcurrentHashMap<String, CachedComments>()
    private fun commentCacheKey(postId: String) = "${AuthSession.current?.user?.id.orEmpty()}:$postId"

    override suspend fun feed(cursor: String?) = api.feed(cursor).bodyOrThrow()
    override suspend fun get(id: String) = api.get(id).bodyOrThrow()
    override suspend fun create(id: String, content: String, images: List<File>, progress: (Float) -> Unit): FeedPost {
        val tracker = UploadTracker(images.sumOf(File::length).coerceAtLeast(1), progress)
        val parts = images.mapIndexed { index, file ->
            MultipartBody.Part.createFormData("media", "photo-${index + 1}.jpg", ProgressImageBody(file, tracker))
        }
        return api.create(
            id.toRequestBody("text/plain".toMediaType()),
            content.toRequestBody("text/plain".toMediaType()),
            parts,
        ).bodyOrThrow()
    }
    override suspend fun like(id: String, liked: Boolean) = (if (liked) api.like(id) else api.unlike(id)).bodyOrThrow()
    override suspend fun delete(id: String) { api.delete(id).requireSuccess() }
    override suspend fun comments(id: String, cursor: String?): FeedCommentPage {
        val page = api.comments(id, cursor).bodyOrThrow()
        if (cursor == null) commentCache[commentCacheKey(id)] = CachedComments(page, System.currentTimeMillis())
        return page
    }
    override fun cachedComments(id: String): FeedCommentPage? {
        val key = commentCacheKey(id)
        val cached = commentCache[key] ?: return null
        if (System.currentTimeMillis() - cached.storedAt > COMMENT_CACHE_MS) {
            commentCache.remove(key, cached)
            return null
        }
        return cached.page
    }
    override suspend fun prefetchComments(id: String) {
        if (cachedComments(id) == null) comments(id)
    }
    override suspend fun comment(id: String, body: AddFeedComment): FeedComment {
        val saved = api.comment(id, body).bodyOrThrow()
        updateCachedComments(id) { page -> page.copy(items = page.items.upsertComment(saved)) }
        return saved
    }
    override suspend fun likeComment(id: String, commentId: String, liked: Boolean): FeedComment {
        val updated = (if (liked) api.likeComment(id, commentId) else api.unlikeComment(id, commentId)).bodyOrThrow()
        updateCachedComments(id) { page -> page.copy(items = page.items.updateComment(updated)) }
        return updated
    }
    override suspend fun deleteComment(id: String, commentId: String) {
        api.deleteComment(id, commentId).requireSuccess()
        updateCachedComments(id) { page -> page.copy(items = page.items.removeComment(commentId)) }
    }

    private fun updateCachedComments(postId: String, transform: (FeedCommentPage) -> FeedCommentPage) {
        val key = commentCacheKey(postId)
        commentCache.computeIfPresent(key) { _, cached -> CachedComments(transform(cached.page), System.currentTimeMillis()) }
    }

    private fun Response<*>.requireSuccess() {
        if (isSuccessful) return
        val message = if (code() == 401) "Session expired. Please sign in again." else {
            errorBody()?.use { body -> runCatching { ApiClient.json.decodeFromString<FeedApiError>(body.string()).message }.getOrNull() }
                ?: "Request failed (${code()}). Please retry."
        }
        throw FeedApiException(code(), message)
    }
    private fun <T> Response<T>.bodyOrThrow(): T { requireSuccess(); return body() ?: throw FeedApiException(502, "Server returned an empty response.") }
}

private const val COMMENT_CACHE_MS = 2 * 60 * 1000L

private fun List<FeedComment>.updateComment(updated: FeedComment): List<FeedComment> = map { root ->
    when {
        root.id == updated.id -> updated.copy(replies = root.replies)
        root.replies.any { it.id == updated.id } -> root.copy(replies = root.replies.map { if (it.id == updated.id) updated else it })
        else -> root
    }
}

private fun List<FeedComment>.upsertComment(comment: FeedComment): List<FeedComment> {
    if (any { it.id == comment.id || it.replies.any { reply -> reply.id == comment.id } }) return updateComment(comment)
    val parentId = comment.parentId
    return if (parentId == null) listOf(comment) + this
    else map { root -> if (root.id == parentId) root.copy(replies = root.replies + comment) else root }
}

private fun List<FeedComment>.removeComment(commentId: String): List<FeedComment> =
    filterNot { it.id == commentId }.map { it.copy(replies = it.replies.filterNot { reply -> reply.id == commentId }) }

private class UploadTracker(private val total: Long, private val progress: (Float) -> Unit) {
    private var sent = 0L
    @Synchronized fun advance(bytes: Int) {
        sent += bytes
        progress((sent.toFloat() / total).coerceIn(0f, 1f))
    }
}
private class ProgressImageBody(private val file: File, private val tracker: UploadTracker) : RequestBody() {
    override fun contentType() = "image/jpeg".toMediaType()
    override fun contentLength() = file.length()
    override fun writeTo(sink: BufferedSink) {
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                sink.write(buffer, 0, count); tracker.advance(count)
            }
        }
    }
}
