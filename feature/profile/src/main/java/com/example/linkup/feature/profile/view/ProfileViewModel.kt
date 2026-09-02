package com.example.linkup.feature.profile.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.model.Profile
import com.example.linkup.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /** Null means "the signed-in user". */
    private var target: String? = null
    private var loadedOnce = false

    /**
     * Loads a profile.
     *
     * Re-entering the screen for a target already on screen refreshes in place
     * instead of flashing the skeleton — which is also what makes an edit made on
     * the edit screen show up on the way back. This view model is activity-scoped
     * while the app still routes with [com.example.linkup.core.navigation.AppNavigator]
     * rather than a NavHost, so it survives that round trip.
     */
    fun load(userId: String? = null, force: Boolean = false) {
        val sameTarget = loadedOnce && target == userId
        target = userId
        loadedOnce = true

        if (sameTarget && !force && _uiState.value is ProfileUiState.Ready) {
            refresh()
            return
        }
        _uiState.value = ProfileUiState.Loading
        fetch()
    }

    /** Re-fetches while keeping the current content on screen. */
    fun refresh() {
        val current = _uiState.value
        if (current is ProfileUiState.Ready) {
            _uiState.value = current.copy(isRefreshing = true, message = null)
        }
        fetch()
    }

    fun toggleFollow() {
        val current = _uiState.value as? ProfileUiState.Ready ?: return
        if (current.profile.isMe || current.followInFlight) return

        val wasFollowing = current.profile.isFollowing
        // Optimistic: the button reacts immediately, and rolls back on failure.
        _uiState.value = current.copy(
            profile = current.profile.withFollowState(!wasFollowing),
            followInFlight = true,
            message = null
        )

        viewModelScope.launch {
            profileRepository.setFollowing(current.profile.id, !wasFollowing)
                .onSuccess { isFollowing ->
                    _uiState.update { state ->
                        (state as? ProfileUiState.Ready)?.copy(
                            profile = state.profile.withFollowState(isFollowing),
                            followInFlight = false
                        ) ?: state
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        (state as? ProfileUiState.Ready)?.copy(
                            profile = state.profile.withFollowState(wasFollowing),
                            followInFlight = false,
                            message = error.message ?: "Could not update follow state"
                        ) ?: state
                    }
                }
        }
    }

    /** Clears cached state on logout so the next account never sees the last one's data. */
    fun reset() {
        loadedOnce = false
        target = null
        _uiState.value = ProfileUiState.Loading
    }

    fun consumeMessage() {
        _uiState.update { state ->
            (state as? ProfileUiState.Ready)?.copy(message = null) ?: state
        }
    }

    private fun fetch() {
        viewModelScope.launch {
            val result = target?.let { profileRepository.getProfile(it) }
                ?: profileRepository.getMyProfile()

            result
                .onSuccess { profile -> _uiState.value = ProfileUiState.Ready(profile) }
                .onFailure { error ->
                    val current = _uiState.value
                    // Keep whatever is already on screen rather than replacing it with an error page.
                    _uiState.value = if (current is ProfileUiState.Ready) {
                        current.copy(
                            isRefreshing = false,
                            message = error.message ?: "Could not refresh this profile"
                        )
                    } else {
                        ProfileUiState.Error(error.message ?: "Could not load this profile")
                    }
                }
        }
    }

    /** Keeps the follower figure consistent with the button the user just tapped. */
    private fun Profile.withFollowState(following: Boolean): Profile {
        if (following == isFollowing) return this
        val delta = if (following) 1 else -1
        return copy(
            isFollowing = following,
            followerCount = (followerCount + delta).coerceAtLeast(0)
        )
    }
}
