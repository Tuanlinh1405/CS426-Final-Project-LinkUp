package com.example.linkup.data.reels

import com.example.linkup.data.network.ApiClient
import com.example.linkup.data.network.AuthSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import retrofit2.Response
import java.io.Closeable
import java.io.File

interface ReelRepository {
    suspend fun feed(cursor: String? = null, author: String? = null): ReelPage
    suspend fun get(id: String): Reel
    suspend fun like(id: String, liked: Boolean): Reel
    suspend fun delete(id: String)
    suspend fun hide(id: String, hidden: Boolean = true)
    suspend fun comments(id: String, cursor: String? = null): CommentPage
    suspend fun comment(id: String, comment: AddComment): ReelComment
    suspend fun deleteComment(id: String, commentId: String)
    suspend fun upload(id: String, caption: String, video: File, thumbnail: File?, progress: (Float) -> Unit): Reel
    fun watch(id: String, event: WatchEvent)
}

class ReelRepositoryImpl(private val api: ReelApi = ApiClient.retrofit.create(ReelApi::class.java)) : ReelRepository, Closeable {
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class Queued(val user: String, val reel: String, val event: WatchEvent)
    private val events = Channel<Queued>(64)
    init {
        eventScope.launch {
            for (queued in events) {
                for (attempt in 0..2) {
                    if (AuthSession.current?.user?.id != queued.user) break
                    try { api.watch(queued.reel, queued.event).requireSuccess(); break }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        if (error is ReelApiException && error.status in 400..499) break
                        if (attempt < 2) delay(500L * (attempt + 1))
                    }
                }
            }
        }
    }
    override suspend fun feed(cursor: String?, author: String?) = api.feed(cursor, author).bodyOrThrow()
    override suspend fun get(id: String) = api.get(id).bodyOrThrow()
    override suspend fun like(id: String, liked: Boolean) = (if (liked) api.like(id) else api.unlike(id)).bodyOrThrow()
    override suspend fun delete(id: String) { api.delete(id).requireSuccess() }
    override suspend fun hide(id: String, hidden: Boolean) { (if (hidden) api.hide(id) else api.unhide(id)).requireSuccess() }
    override suspend fun comments(id: String, cursor: String?) = api.comments(id, cursor).bodyOrThrow()
    override suspend fun comment(id: String, comment: AddComment) = api.comment(id, comment).bodyOrThrow()
    override suspend fun deleteComment(id: String, commentId: String) { api.deleteComment(id, commentId).requireSuccess() }
    override suspend fun upload(id: String, caption: String, video: File, thumbnail: File?, progress: (Float) -> Unit): Reel {
        val text = "text/plain".toMediaType()
        val videoPart = MultipartBody.Part.createFormData("video", "reel.mp4", ProgressBody(video, progress))
        val image = thumbnail?.let { MultipartBody.Part.createFormData("thumbnail", "thumbnail.jpg", it.asRequestBody("image/jpeg".toMediaType())) }
        return api.upload(id.toRequestBody(text), caption.toRequestBody(text), videoPart, image).bodyOrThrow()
    }
    override fun watch(id: String, event: WatchEvent) {
        AuthSession.current?.user?.id?.let { events.trySend(Queued(it, id, event)) }
    }
    override fun close() { events.close(); eventScope.cancel() }
    private fun Response<*>.requireSuccess() {
        if (isSuccessful) return
        val message = if (code() == 401) "Session expired. Please sign in again." else {
            errorBody()?.use { body -> runCatching { ApiClient.json.decodeFromString<ReelApiError>(body.string()).message }.getOrNull() }
                ?: "Request failed (${code()}). Please retry."
        }
        throw ReelApiException(code(), message)
    }
    private fun <T> Response<T>.bodyOrThrow(): T { requireSuccess(); return body() ?: throw ReelApiException(502, "Server returned an empty response.") }
}

private class ProgressBody(private val file: File, private val progress: (Float) -> Unit) : RequestBody() {
    override fun contentType() = "video/mp4".toMediaType()
    override fun contentLength() = file.length()
    override fun writeTo(sink: BufferedSink) {
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024); var sent = 0L
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                sink.write(buffer, 0, count); sent += count
                progress((sent.toFloat() / file.length().coerceAtLeast(1)).coerceIn(0f, 1f))
            }
        }
    }
}
