package com.example.linkup.data.remote.api

import com.example.linkup.data.remote.dto.FollowStateDto
import com.example.linkup.data.remote.dto.MediaUploadResponseDto
import com.example.linkup.data.remote.dto.ProfileDto
import com.example.linkup.data.remote.dto.UpdateProfileRequestDto
import com.example.linkup.data.remote.dto.UserSummaryPageDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ProfileApiService {

    @GET("profile/me")
    suspend fun getMyProfile(): Response<ProfileDto>

    /** [id] accepts a user UUID or a username. */
    @GET("profile/{id}")
    suspend fun getProfile(@Path("id") id: String): Response<ProfileDto>

    @PATCH("profile/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequestDto): Response<ProfileDto>

    @Multipart
    @POST("profile/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<MediaUploadResponseDto>

    @Multipart
    @POST("profile/me/cover")
    suspend fun uploadCover(@Part file: MultipartBody.Part): Response<MediaUploadResponseDto>

    @DELETE("profile/me/avatar")
    suspend fun deleteAvatar(): Response<ProfileDto>

    @DELETE("profile/me/cover")
    suspend fun deleteCover(): Response<ProfileDto>

    @POST("profile/{id}/follow")
    suspend fun follow(@Path("id") id: String): Response<FollowStateDto>

    @DELETE("profile/{id}/follow")
    suspend fun unfollow(@Path("id") id: String): Response<FollowStateDto>

    @GET("users/search")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<UserSummaryPageDto>

    @GET("profile/{id}/followers")
    suspend fun followers(
        @Path("id") id: String,
        @Query("cursor") cursor: String? = null
    ): Response<UserSummaryPageDto>

    @GET("profile/{id}/following")
    suspend fun following(
        @Path("id") id: String,
        @Query("cursor") cursor: String? = null
    ): Response<UserSummaryPageDto>
}
