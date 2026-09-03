package com.example.linkup.feature.dating

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

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
    val maxAge: Int? = null
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
    val compatibilityScore: Int = 0
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

    @GET("dating/discover")
    suspend fun getDiscoverCandidates(): Response<List<DatingCandidateResponseDto>>

    @POST("dating/swipes")
    suspend fun swipe(@Body request: SwipeRequestDto): Response<SwipeResponseDto>

    @GET("dating/matches")
    suspend fun getMatches(): Response<List<MatchResponseDto>>
}
