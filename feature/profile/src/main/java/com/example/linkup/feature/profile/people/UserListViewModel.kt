package com.example.linkup.feature.profile.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.model.UserSummary
import com.example.linkup.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class UserListMode { FOLLOWERS, FOLLOWING }

data class UserListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val items: List<UserSummary> = emptyList(),
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val total: Int = 0,
    val busyIds: Set<String> = emptySet(),
    val message: String? = null
) {
    val hasMore: Boolean get() = nextCursor != null
    val isEmpty: Boolean get() = items.isEmpty() && !isLoading && error == null
}

/** Backs both the followers and the following list; they differ only in the query. */
@HiltViewModel
class UserListViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserListUiState())
    val uiState: StateFlow<UserListUiState> = _uiState.asStateFlow()

    private var loadedKey: String? = null

    fun load(userId: String, mode: UserListMode, force: Boolean = false) {
        val key = "$mode:$userId"
        if (loadedKey == key && !force) return
        loadedKey = key

        _uiState.value = UserListUiState(isLoading = true)
        viewModelScope.launch { fetch(userId, mode, cursor = null) }
    }

    fun loadMore(userId: String, mode: UserListMode) {
        val state = _uiState.value
        val cursor = state.nextCursor ?: return
        if (state.isLoadingMore || state.isLoading) return

        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch { fetch(userId, mode, cursor) }
    }

    /** Optimistic follow toggle, rolled back if the server disagrees. */
    fun toggleFollow(user: UserSummary) {
        if (user.isMe || user.id in _uiState.value.busyIds) return
        val target = !user.isFollowing

        _uiState.update { state ->
            state.copy(
                items = state.items.map { if (it.id == user.id) it.copy(isFollowing = target) else it },
                busyIds = state.busyIds + user.id
            )
        }

        viewModelScope.launch {
            profileRepository.setFollowing(user.id, target)
                .onSuccess { following ->
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.map {
                                if (it.id == user.id) it.copy(isFollowing = following) else it
                            },
                            busyIds = state.busyIds - user.id
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.map {
                                if (it.id == user.id) it.copy(isFollowing = !target) else it
                            },
                            busyIds = state.busyIds - user.id,
                            message = error.message ?: "Could not update follow state"
                        )
                    }
                }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Drops everything, so the next user of this activity-scoped model starts clean. */
    fun reset() {
        loadedKey = null
        _uiState.value = UserListUiState()
    }

    private suspend fun fetch(userId: String, mode: UserListMode, cursor: String?) {
        val result = when (mode) {
            UserListMode.FOLLOWERS -> profileRepository.followers(userId, cursor)
            UserListMode.FOLLOWING -> profileRepository.following(userId, cursor)
        }
        result
            .onSuccess { page ->
                _uiState.update { state ->
                    val existing = state.items.map { it.id }.toSet()
                    state.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = null,
                        items = if (cursor == null) {
                            page.items
                        } else {
                            state.items + page.items.filterNot { it.id in existing }
                        },
                        nextCursor = page.nextCursor,
                        total = page.total
                    )
                }
            }
            .onFailure { error ->
                _uiState.update { state ->
                    if (state.items.isNotEmpty()) {
                        state.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            message = error.message ?: "Could not load more"
                        )
                    } else {
                        state.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = error.message ?: "Could not load this list"
                        )
                    }
                }
            }
    }
}
