package com.example.linkup.feature.more.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.model.UserSummary
import com.example.linkup.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val results: List<UserSummary> = emptyList(),
    val nextCursor: String? = null,
    val total: Int = 0,
    val error: String? = null,
    val busyIds: Set<String> = emptySet(),
    val message: String? = null,
    /** True once a query has actually been run, so "no results" is not shown too early. */
    val hasSearched: Boolean = false
) {
    val hasMore: Boolean get() = nextCursor != null

    val showEmptyResult: Boolean
        get() = hasSearched && !isSearching && results.isEmpty() && error == null && query.isNotBlank()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private companion object {
        /** Long enough to skip a keystroke burst, short enough to feel live. */
        const val DEBOUNCE_MS = 300L
    }

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Debounced by hand rather than with `Flow.debounce`, which is still marked
     * `@FlowPreview`; cancelling the previous job does the same job with stable API.
     */
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, error = null) }
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    nextCursor = null,
                    total = 0,
                    isSearching = false,
                    hasSearched = false
                )
            }
            return
        }

        _uiState.update { it.copy(isSearching = true) }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            runSearch(query, cursor = null)
        }
    }

    fun retry() {
        val query = _uiState.value.query
        if (query.isBlank()) return
        _uiState.update { it.copy(isSearching = true, error = null) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(query, cursor = null) }
    }

    fun loadMore() {
        val state = _uiState.value
        val cursor = state.nextCursor ?: return
        if (state.isLoadingMore || state.isSearching) return

        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch { runSearch(state.query, cursor) }
    }

    fun toggleFollow(user: UserSummary) {
        if (user.isMe || user.id in _uiState.value.busyIds) return
        val target = !user.isFollowing

        _uiState.update { state ->
            state.copy(
                results = state.results.map { if (it.id == user.id) it.copy(isFollowing = target) else it },
                busyIds = state.busyIds + user.id
            )
        }

        viewModelScope.launch {
            profileRepository.setFollowing(user.id, target)
                .onSuccess { following ->
                    _uiState.update { state ->
                        state.copy(
                            results = state.results.map {
                                if (it.id == user.id) it.copy(isFollowing = following) else it
                            },
                            busyIds = state.busyIds - user.id
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        state.copy(
                            results = state.results.map {
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

    fun reset() {
        searchJob?.cancel()
        _uiState.value = SearchUiState()
    }

    private suspend fun runSearch(query: String, cursor: String?) {
        profileRepository.searchUsers(query, cursor)
            .onSuccess { page ->
                _uiState.update { state ->
                    // A slower earlier request must not overwrite newer results.
                    if (state.query != query) return@update state
                    val existing = state.results.map { it.id }.toSet()
                    state.copy(
                        isSearching = false,
                        isLoadingMore = false,
                        hasSearched = true,
                        error = null,
                        results = if (cursor == null) {
                            page.items
                        } else {
                            state.results + page.items.filterNot { it.id in existing }
                        },
                        nextCursor = page.nextCursor,
                        total = page.total
                    )
                }
            }
            .onFailure { error ->
                _uiState.update { state ->
                    if (state.query != query) return@update state
                    state.copy(
                        isSearching = false,
                        isLoadingMore = false,
                        hasSearched = true,
                        error = error.message ?: "Search failed"
                    )
                }
            }
    }
}
