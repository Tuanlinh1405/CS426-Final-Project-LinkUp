package com.example.linkup.data.repository

import com.example.linkup.data.model.FriendshipState
import com.example.linkup.data.model.UserSummaryPage
import kotlinx.coroutines.flow.SharedFlow

data class FriendRealtimeUpdate(
    val incomingRequestCount: Int
)

interface FriendRepository {

    val realtimeUpdates: SharedFlow<FriendRealtimeUpdate>

    /** [userId] null means the signed-in user's own friends. */
    suspend fun friends(userId: String?, cursor: String?): Result<UserSummaryPage>

    suspend fun incomingRequests(cursor: String?): Result<UserSummaryPage>

    suspend fun outgoingRequests(cursor: String?): Result<UserSummaryPage>

    /** Pending requests waiting on the user — drives the Friends badge. */
    suspend fun incomingRequestCount(): Result<Int>

    /** "People you may know", ranked by mutual friends. */
    suspend fun suggestions(): Result<UserSummaryPage>

    suspend fun state(userId: String): Result<FriendshipState>

    suspend fun sendRequest(userId: String): Result<FriendshipState>

    suspend fun cancelRequest(userId: String): Result<FriendshipState>

    suspend fun accept(userId: String): Result<FriendshipState>

    suspend fun decline(userId: String): Result<FriendshipState>

    suspend fun unfriend(userId: String): Result<FriendshipState>
}
