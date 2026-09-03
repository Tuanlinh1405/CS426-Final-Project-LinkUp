package com.example.linkup.feature.dating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.model.User
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DatingViewModel(
    private val repository: DatingRepository,
    currentUser: User
) : ViewModel() {
    private val _uiState = MutableStateFlow(DatingUiState(profile = repository.getProfile() ?: defaultDatingProfile(currentUser)))
    val uiState: StateFlow<DatingUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DatingEffect>()
    val effects: SharedFlow<DatingEffect> = _effects.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(
            error = null,
            candidates = repository.getDiscoverCandidates(),
            matches = repository.getMatches(),
            isLoading = false
        )
    }

    fun saveProfile(profile: DatingProfile) {
        runAction(isSaving = true) {
            repository.updateProfile(profile)
            _uiState.value.copy(profile = profile)
        }
    }

    fun swipe(decision: SwipeDecision) {
        val candidate = _uiState.value.candidates.firstOrNull() ?: return
        runAction(isSwiping = true) {
            val result = repository.swipe(candidate.user.id, decision)
            if (result.isMatch && result.match != null) {
                viewModelScope.launch { _effects.emit(DatingEffect.MatchCreated(result.match)) }
            }
            _uiState.value.copy(
                candidates = repository.getDiscoverCandidates(),
                matches = repository.getMatches()
            )
        }
    }

    fun reviewPassedCandidates() {
        repository.resetPassedCandidates()
        refresh()
    }

    private fun runAction(
        isSaving: Boolean = false,
        isSwiping: Boolean = false,
        action: () -> DatingUiState
    ) {
        try {
            _uiState.value = _uiState.value.copy(isSaving = isSaving, isSwiping = isSwiping, error = null)
            _uiState.value = action().copy(isSaving = false, isSwiping = false)
        } catch (exception: IllegalArgumentException) {
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                isSwiping = false,
                error = exception.message ?: "Dating action failed"
            )
        }
    }
}
