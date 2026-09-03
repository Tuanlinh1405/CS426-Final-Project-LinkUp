package com.example.linkup.feature.dating

import com.example.linkup.data.model.User

sealed interface DatingEffect {
    data class MatchCreated(val match: DatingMatch) : DatingEffect
}

data class DatingUiState(
    val profile: DatingProfile? = null,
    val candidates: List<DatingCandidate> = emptyList(),
    val matches: List<DatingMatch> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSwiping: Boolean = false,
    val error: String? = null
)

fun defaultDatingProfile(user: User): DatingProfile = DatingProfile(
    userId = user.id,
    bio = "Designer, weekend hiker, and coffee lover.",
    interests = listOf("Travel", "Design", "Coffee"),
    lookingFor = LookingFor.RELATIONSHIP,
    preferredGender = "ANY",
    minAge = 20,
    maxAge = 30
)
