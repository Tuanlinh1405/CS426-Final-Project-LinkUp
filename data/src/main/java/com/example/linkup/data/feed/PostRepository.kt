package com.example.linkup.data.feed

import com.example.linkup.data.network.ApiClient
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import retrofit2.Response
import java.io.File

interface PostRepository {
    suspend fun feed(cursor: String? = null): FeedPage
    suspend fun get(id: String): FeedPost
    suspend fun create(id: String, content: String, images: List<File>, progress: (Float) -> Unit): FeedPost
    suspend fun like(id: String, liked: Boolean): FeedPost
    suspend fun delete(id: String)
    suspend fun comments(id: String, cursor: String? = null): FeedCommentPage
    suspend fun comment(id: String, body: AddFeedComment): FeedComment
    suspend fun deleteComment(id: String, commentId: String)
}

class PostRepositoryImpl(private val api: PostApi = ApiClient.retrofit.create(PostApi::class.java)) : PostRepository {
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
    override suspend fun comments(id: String, cursor: String?) = api.comments(id, cursor).bodyOrThrow()
    override suspend fun comment(id: String, body: AddFeedComment) = api.comment(id, body).bodyOrThrow()
    override suspend fun deleteComment(id: String, commentId: String) { api.deleteComment(id, commentId).requireSuccess() }

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
