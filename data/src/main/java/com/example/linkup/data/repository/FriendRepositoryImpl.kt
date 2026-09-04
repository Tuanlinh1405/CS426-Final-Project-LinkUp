package com.example.linkup.data.repository

import com.example.linkup.data.mapper.toDomain
import com.example.linkup.data.model.FriendshipState
import com.example.linkup.data.model.UserSummaryPage
import com.example.linkup.data.remote.api.FriendApiService
import com.example.linkup.data.remote.dto.ApiErrorDto
import com.example.linkup.data.remote.websocket.ChatWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val api: FriendApiService,
    private val json: Json,
    private val webSocketClient: ChatWebSocketClient
) : FriendRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _realtimeUpdates = MutableSharedFlow<FriendRealtimeUpdate>(extraBufferCapacity = 32)
    override val realtimeUpdates: SharedFlow<FriendRealtimeUpdate> = _realtimeUpdates.asSharedFlow()

    init {
        scope.launch {
            webSocketClient.incomingFrames.collect { frame ->
                if (frame.event == "NOTIFICATIONS_CHANGED" && frame.pendingFriendRequests != null) {
                    _realtimeUpdates.tryEmit(FriendRealtimeUpdate(frame.pendingFriendRequests))
                }
            }
        }
    }

    override suspend fun friends(userId: String?, cursor: String?): Result<UserSummaryPage> =
        call { api.friends(userId?.removePrefix("@"), cursor) }.map { it.toDomain() }

    override suspend fun incomingRequests(cursor: String?): Result<UserSummaryPage> =
        call { api.incoming(cursor) }.map { it.toDomain() }

    override suspend fun outgoingRequests(cursor: String?): Result<UserSummaryPage> =
        call { api.outgoing(cursor) }.map { it.toDomain() }

    override suspend fun incomingRequestCount(): Result<Int> =
        call { api.requestCount() }.map { it.unreadCount }

    override suspend fun suggestions(): Result<UserSummaryPage> =
        call { api.suggestions() }.map { it.toDomain() }

    override suspend fun state(userId: String): Result<FriendshipState> =
        call { api.state(userId.removePrefix("@")) }.map { it.toDomain() }

    override suspend fun sendRequest(userId: String): Result<FriendshipState> =
        call { api.sendRequest(userId) }.map { it.toDomain() }

    override suspend fun cancelRequest(userId: String): Result<FriendshipState> =
        call { api.cancelRequest(userId) }.map { it.toDomain() }

    override suspend fun accept(userId: String): Result<FriendshipState> =
        call { api.accept(userId) }.map { it.toDomain() }

    override suspend fun decline(userId: String): Result<FriendshipState> =
        call { api.decline(userId) }.map { it.toDomain() }

    override suspend fun unfriend(userId: String): Result<FriendshipState> =
        call { api.unfriend(userId) }.map { it.toDomain() }

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
        // A 409 carries the rule that was broken, which is the most useful message here.
        raw?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString<ApiErrorDto>(it) }.getOrNull() }
            ?.let { return it.message }

        return when (code()) {
            401 -> "Your session expired. Please sign in again."
            404 -> "That account no longer exists."
            409 -> "That's already changed — pull to refresh."
            in 500..599 -> "The server had a problem. Try again in a moment."
            else -> "Something went wrong (HTTP ${code()})."
        }
    }
}
