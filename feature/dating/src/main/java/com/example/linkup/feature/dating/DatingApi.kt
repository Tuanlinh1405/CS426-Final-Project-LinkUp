package com.example.linkup.feature.dating

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

@Serializable
data class DatingProfileRequestDto(
    val bio: String? = null,
    val interests: List<String> = emptyList(),
    val lookingFor: String = "RELATIONSHIP",
    val preferredGender: String? = null,
    val minAge: Int? = null,
    val maxAge: Int? = null
)

@Serializable
data class DatingProfileResponseDto(
    val userId: String? = null,
    val bio: String? = null,
    val interests: List<String> = emptyList(),
    val lookingFor: String = "RELATIONSHIP",
    val preferredGender: String? = null,
    val minAge: Int? = null,
    val maxAge: Int? = null,
    val photos: List<DatingPhotoDto> = emptyList(),
    val name: String? = null,
    val username: String? = null,
    val initials: String? = null,
    val age: Int? = null,
    val avatarUrl: String? = null
)

@Serializable
data class DatingPhotoDto(
    val id: String,
    val photoUrl: String,
    val displayOrder: Int
)

@Serializable
data class DatingCandidateResponseDto(
    val userId: String,
    val name: String,
    val username: String,
    val initials: String,
    val age: Int? = null,
    val bio: String? = null,
    val interests: List<String> = emptyList(),
    val likedYou: Boolean = false,
    val compatibilityScore: Int = 0,
    val photoUrl: String? = null
)

@Serializable
data class SwipeRequestDto(val targetUserId: String, val decision: String)

@Serializable
data class MatchResponseDto(
    val id: String,
    val userId: String,
    val name: String,
    val username: String,
    val initials: String,
    val createdAt: String
)

@Serializable
data class SwipeResponseDto(
    val decision: String,
    val isMatch: Boolean,
    val match: MatchResponseDto? = null
)

interface DatingApiService {
    @GET("dating/profile")
    suspend fun getProfile(): Response<DatingProfileResponseDto>

    @PUT("dating/profile")
    suspend fun updateProfile(@Body request: DatingProfileRequestDto): Response<DatingProfileResponseDto>

    @Multipart
    @POST("dating/profile/photos")
    suspend fun uploadPhoto(@Part file: MultipartBody.Part): Response<List<DatingPhotoDto>>

    @DELETE("dating/profile/photos/{id}")
    suspend fun deletePhoto(@Path("id") id: String): Response<List<DatingPhotoDto>>

    @GET("dating/discover")
    suspend fun getDiscoverCandidates(): Response<List<DatingCandidateResponseDto>>

    @POST("dating/swipes")
    suspend fun swipe(@Body request: SwipeRequestDto): Response<SwipeResponseDto>

    @POST("dating/swipes/reset-passed")
    suspend fun resetPassedSwipes(): Response<Unit>

    @GET("dating/matches")
    suspend fun getMatches(): Response<List<MatchResponseDto>>
}
