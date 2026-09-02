package com.example.linkup.feature.profile.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.model.FriendshipStatus
import com.example.linkup.data.model.UserSummary
import com.example.linkup.data.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FriendsTab { FRIENDS, REQUESTS, SUGGESTIONS }

data class FriendsUiState(
    val tab: FriendsTab = FriendsTab.FRIENDS,
    val isLoading: Boolean = true,
    val error: String? = null,
    val items: List<UserSummary> = emptyList(),
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val total: Int = 0,
    val friendCount: Int = 0,
    val requestCount: Int = 0,
    val busyIds: Set<String> = emptySet(),
    val message: String? = null,
    val messageIsError: Boolean = false
) {
    val hasMore: Boolean get() = nextCursor != null
    val isEmpty: Boolean get() = items.isEmpty() && !isLoading && error == null
}

/**
 * Friends, incoming requests and suggestions behind one tabbed screen.
 *
 * Actions apply optimistically and roll back on failure, and a row that no longer
 * belongs on the current tab is removed rather than left showing a stale control —
 * confirming a request should make it leave the Requests list.
 */
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private var loadedOnce = false

    fun load(force: Boolean = false) {
        if (loadedOnce && !force) {
            refreshCounts()
            return
        }
        loadedOnce = true
        fetch(_uiState.value.tab, cursor = null, showSkeleton = true)
        refreshCounts()
    }

    fun setTab(tab: FriendsTab) {
        if (_uiState.value.tab == tab) return
        _uiState.update {
            it.copy(tab = tab, items = emptyList(), nextCursor = null, isLoading = true, error = null)
        }
        fetch(tab, cursor = null, showSkeleton = true)
    }

    fun refresh() {
        fetch(_uiState.value.tab, cursor = null, showSkeleton = false)
        refreshCounts()
    }

    fun loadMore() {
        val state = _uiState.value
        val cursor = state.nextCursor ?: return
        if (state.isLoadingMore || state.isLoading) return
        _uiState.update { it.copy(isLoadingMore = true) }
        fetch(state.tab, cursor, showSkeleton = false)
    }

    /** Keeps the tab badge honest without disturbing the list. */
    fun refreshCounts() {
        viewModelScope.launch {
            friendRepository.incomingRequestCount()
                .onSuccess { count -> _uiState.update { it.copy(requestCount = count) } }
        }
    }

    fun sendRequest(user: UserSummary) = act(user, "Request sent") {
        friendRepository.sendRequest(user.id)
    }

    fun cancelRequest(user: UserSummary) = act(user, "Request cancelled") {
        friendRepository.cancelRequest(user.id)
    }

    fun accept(user: UserSummary) = act(user, "You're now friends") {
        friendRepository.accept(user.id)
    }

    fun decline(user: UserSummary) = act(user, "Request removed") {
        friendRepository.decline(user.id)
    }

    fun unfriend(user: UserSummary) = act(user, "Removed from friends") {
        friendRepository.unfriend(user.id)
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun reset() {
        loadedOnce = false
        _uiState.value = FriendsUiState()
    }

    // ---- internals -------------------------------------------------------

    private inline fun act(
        user: UserSummary,
        successMessage: String,
        crossinline action: suspend () -> Result<com.example.linkup.data.model.FriendshipState>
    ) {
        if (user.id in _uiState.value.busyIds) return
        val before = _uiState.value
        _uiState.update { it.copy(busyIds = it.busyIds + user.id, message = null) }

        viewModelScope.launch {
            action()
                .onSuccess { result ->
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.reconcile(user.id, result.status, state.tab),
                            busyIds = state.busyIds - user.id,
                            friendCount = result.friendCount,
                            requestCount = result.incomingRequestCount,
                            message = successMessage,
                            messageIsError = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            items = before.items,
                            busyIds = it.busyIds - user.id,
                            message = error.message ?: "That didn't work",
                            messageIsError = true
                        )
                    }
                }
        }
    }

    /**
     * Applies a new status to a row, dropping it when it no longer belongs here.
     *
     * A confirmed request leaves the Requests tab; an added suggestion stays put but
     * flips to "Requested" so the user can undo it without hunting for them again.
     */
    private fun List<UserSummary>.reconcile(
        userId: String,
        status: FriendshipStatus,
        tab: FriendsTab
    ): List<UserSummary> {
        val leaves = when (tab) {
            FriendsTab.REQUESTS -> status != FriendshipStatus.REQUEST_RECEIVED
            FriendsTab.FRIENDS -> status != FriendshipStatus.FRIENDS
            FriendsTab.SUGGESTIONS -> false
        }
        return if (leaves) {
            filterNot { it.id == userId }
        } else {
            map { if (it.id == userId) it.copy(friendship = status) else it }
        }
    }

    private fun fetch(tab: FriendsTab, cursor: String?, showSkeleton: Boolean) {
        if (showSkeleton) _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val result = when (tab) {
                FriendsTab.FRIENDS -> friendRepository.friends(null, cursor)
                FriendsTab.REQUESTS -> friendRepository.incomingRequests(cursor)
                FriendsTab.SUGGESTIONS -> friendRepository.suggestions()
            }

            result
                .onSuccess { page ->
                    _uiState.update { state ->
                        // A tab switch may have landed while this was in flight.
                        if (state.tab != tab) return@update state
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
                            total = page.total,
                            friendCount = if (tab == FriendsTab.FRIENDS) page.total else state.friendCount
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        if (state.tab != tab) return@update state
                        if (state.items.isNotEmpty()) {
                            state.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                message = error.message ?: "Could not refresh",
                                messageIsError = true
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
}
