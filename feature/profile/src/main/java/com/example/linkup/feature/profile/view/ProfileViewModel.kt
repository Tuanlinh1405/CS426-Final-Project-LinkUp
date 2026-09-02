package com.example.linkup.feature.profile.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.model.FriendshipStatus
import com.example.linkup.data.model.Profile
import com.example.linkup.data.repository.FriendRepository
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
    private val profileRepository: ProfileRepository,
    private val friendRepository: FriendRepository
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

    /**
     * Runs a friend action against the profile on screen.
     *
     * The button switches immediately to [optimistic] and reverts if the server
     * disagrees — a friend request should feel instant, but the control must never
     * end up claiming a relationship that was not actually created.
     */
    private fun friendAction(
        optimistic: FriendshipStatus,
        action: suspend (String) -> Result<com.example.linkup.data.model.FriendshipState>
    ) {
        val current = _uiState.value as? ProfileUiState.Ready ?: return
        if (current.profile.isMe || current.friendActionInFlight) return
        val previous = current.profile.friendship

        _uiState.value = current.copy(
            profile = current.profile.copy(friendship = optimistic),
            friendActionInFlight = true,
            message = null
        )

        viewModelScope.launch {
            action(current.profile.id)
                .onSuccess { result ->
                    _uiState.update { state ->
                        (state as? ProfileUiState.Ready)?.copy(
                            profile = state.profile.copy(
                                friendship = result.status,
                                mutualFriendCount = result.mutualFriendCount
                            ),
                            friendActionInFlight = false
                        ) ?: state
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        (state as? ProfileUiState.Ready)?.copy(
                            profile = state.profile.copy(friendship = previous),
                            friendActionInFlight = false,
                            message = error.message ?: "Could not update that"
                        ) ?: state
                    }
                }
        }
    }

    fun sendFriendRequest() =
        friendAction(FriendshipStatus.REQUEST_SENT) { friendRepository.sendRequest(it) }

    fun cancelFriendRequest() =
        friendAction(FriendshipStatus.NONE) { friendRepository.cancelRequest(it) }

    fun acceptFriendRequest() =
        friendAction(FriendshipStatus.FRIENDS) { friendRepository.accept(it) }

    fun declineFriendRequest() =
        friendAction(FriendshipStatus.NONE) { friendRepository.decline(it) }

    fun unfriend() =
        friendAction(FriendshipStatus.NONE) { friendRepository.unfriend(it) }

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
