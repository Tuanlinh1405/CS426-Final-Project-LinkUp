package com.example.linkup.data.repository

import com.example.linkup.data.mapper.toDomain
import com.example.linkup.data.model.NotificationPage
import com.example.linkup.data.remote.api.NotificationApiService
import com.example.linkup.data.remote.dto.ApiErrorDto
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val api: NotificationApiService,
    private val json: Json
) : NotificationRepository {

    private companion object {
        const val PAGE_SIZE = 25
    }

    override suspend fun load(cursor: String?, unreadOnly: Boolean): Result<NotificationPage> =
        call {
            api.list(
                cursor = cursor,
                limit = PAGE_SIZE,
                filter = if (unreadOnly) "unread" else null
            )
        }.map { it.toDomain() }

    override suspend fun unreadCount(): Result<Int> =
        call { api.unreadCount() }.map { it.unreadCount }

    override suspend fun setRead(id: String, read: Boolean): Result<Int> =
        call { api.setRead(id, read) }.map { it.unreadCount }

    override suspend fun markAllRead(): Result<Int> =
        call { api.markAllRead() }.map { it.unreadCount }

    override suspend fun delete(id: String): Result<Int> =
        call { api.delete(id) }.map { it.unreadCount }

    override suspend fun clearAll(): Result<Int> =
        call { api.clearAll() }.map { it.unreadCount }

    /** Normalises transport and HTTP failures into one readable message. */
    private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> = try {
        val response = block()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(Exception(response.friendlyMessage()))
        }
    } catch (e: IOException) {
        Result.failure(Exception("Can't reach the server. Check that the backend is running."))
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Something went wrong"))
    }

    private fun Response<*>.friendlyMessage(): String {
        val raw = try {
            errorBody()?.string()
        } catch (e: IOException) {
            null
        }
        raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<ApiErrorDto>(it) }.getOrNull() }
            ?.let { return it.message }

        return when (code()) {
            401 -> "Your session expired. Please sign in again."
            404 -> "That notification is no longer there."
            in 500..599 -> "The server had a problem. Try again in a moment."
            else -> "Something went wrong (HTTP ${code()})."
        }
    }
}
