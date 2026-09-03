package com.example.linkup.feature.dating

import com.example.linkup.data.model.User

enum class LookingFor {
    RELATIONSHIP,
    FRIENDSHIP
}

enum class SwipeDecision {
    LIKE,
    PASS
}

data class DatingProfile(
    val userId: String,
    val bio: String,
    val interests: List<String>,
    val lookingFor: LookingFor,
    val preferredGender: String? = null,
    val minAge: Int? = null,
    val maxAge: Int? = null
)

data class DatingCandidate(
    val user: User,
    val age: Int,
    val bio: String,
    val interests: List<String>,
    val distanceKm: Double? = null,
    val photoUrl: String? = null,
    val likedYou: Boolean = false,
    val compatibilityScore: Int = 0
)

data class DatingMatch(
    val id: String,
    val user: User,
    val createdAt: String
)

data class SwipeResult(
    val decision: SwipeDecision,
    val isMatch: Boolean,
    val match: DatingMatch? = null
)
