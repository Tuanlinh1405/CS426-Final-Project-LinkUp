package com.example.linkup.feature.profile.view

import com.example.linkup.data.model.Profile

sealed interface ProfileUiState {

    data object Loading : ProfileUiState

    /** Nothing to show yet — the whole screen is the error. */
    data class Error(val message: String) : ProfileUiState

    data class Ready(
        val profile: Profile,
        val isRefreshing: Boolean = false,
        val followInFlight: Boolean = false,
        val friendActionInFlight: Boolean = false,
        /** Transient feedback shown as an inline banner. */
        val message: String? = null
    ) : ProfileUiState
}
