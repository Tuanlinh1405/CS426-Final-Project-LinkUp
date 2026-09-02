package com.example.linkup.data.remote.api

import com.example.linkup.data.remote.dto.FriendshipStateDto
import com.example.linkup.data.remote.dto.UnreadCountDto
import com.example.linkup.data.remote.dto.UserSummaryPageDto
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface FriendApiService {

    @GET("friends")
    suspend fun friends(
        @Query("of") of: String? = null,
        @Query("cursor") cursor: String? = null
    ): Response<UserSummaryPageDto>

    @GET("friends/requests/incoming")
    suspend fun incoming(@Query("cursor") cursor: String? = null): Response<UserSummaryPageDto>

    @GET("friends/requests/outgoing")
    suspend fun outgoing(@Query("cursor") cursor: String? = null): Response<UserSummaryPageDto>

    @GET("friends/requests/count")
    suspend fun requestCount(): Response<UnreadCountDto>

    @GET("friends/suggestions")
    suspend fun suggestions(): Response<UserSummaryPageDto>

    @GET("friends/{id}/state")
    suspend fun state(@Path("id") id: String): Response<FriendshipStateDto>

    @POST("friends/{id}/request")
    suspend fun sendRequest(@Path("id") id: String): Response<FriendshipStateDto>

    @DELETE("friends/{id}/request")
    suspend fun cancelRequest(@Path("id") id: String): Response<FriendshipStateDto>

    @PUT("friends/{id}/accept")
    suspend fun accept(@Path("id") id: String): Response<FriendshipStateDto>

    @PUT("friends/{id}/decline")
    suspend fun decline(@Path("id") id: String): Response<FriendshipStateDto>

    @DELETE("friends/{id}")
    suspend fun unfriend(@Path("id") id: String): Response<FriendshipStateDto>
}
