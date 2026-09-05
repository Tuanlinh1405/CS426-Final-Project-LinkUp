package com.example.linkup.feature.dating

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
