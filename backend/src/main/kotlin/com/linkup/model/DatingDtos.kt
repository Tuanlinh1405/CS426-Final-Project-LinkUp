package com.linkup.model

import kotlinx.serialization.Serializable

@Serializable
data class DatingProfileRequest(
    val bio: String? = null,
    val interests: List<String> = emptyList(),
    val lookingFor: String = "RELATIONSHIP",
    val preferredGender: String? = null,
    val minAge: Int? = null,
    val maxAge: Int? = null
)

@Serializable
data class DatingProfileResponse(
    val userId: String,
    val bio: String?,
    val interests: List<String>,
    val lookingFor: String,
    val preferredGender: String?,
    val minAge: Int?,
    val maxAge: Int?
)

@Serializable
data class DatingCandidateResponse(
    val userId: String,
    val name: String,
    val username: String,
    val initials: String,
    val age: Int?,
    val bio: String?,
    val interests: List<String>,
    val likedYou: Boolean,
    val compatibilityScore: Int
)

@Serializable
data class SwipeRequest(
    val targetUserId: String,
    val decision: String
)

@Serializable
data class MatchResponse(
    val id: String,
    val userId: String,
    val name: String,
    val username: String,
    val initials: String,
    val createdAt: String
)

@Serializable
data class SwipeResponse(
    val decision: String,
    val isMatch: Boolean,
    val match: MatchResponse? = null
)

@Serializable
data class DatingNotificationResponse(
    val id: String,
    val actorName: String,
    val type: String,
    val targetId: String?,
    val isRead: Boolean,
    val createdAt: String
)
