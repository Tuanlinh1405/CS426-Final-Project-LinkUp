package com.example.linkup.feature.dating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.model.PickedImage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

class DatingViewModel(
    private val repository: DatingRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DatingUiState(isLoading = true))
    val uiState: StateFlow<DatingUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DatingEffect>()
    val effects: SharedFlow<DatingEffect> = _effects.asSharedFlow()
    private val refreshMutex = Mutex()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshMutex.withLock {
                runAction {
                    _uiState.value.copy(
                        error = null,
                        profile = repository.getProfile() ?: _uiState.value.profile,
                        candidates = repository.getDiscoverCandidates(),
                        matches = repository.getMatches(),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun saveProfile(profile: DatingProfile) {
        viewModelScope.launch {
            runAction(isSaving = true) {
                val savedProfile = repository.updateProfile(profile)
                _uiState.value.copy(profile = savedProfile)
            }
        }
    }

    fun uploadPhoto(image: PickedImage) {
        viewModelScope.launch {
            runAction(isSaving = true) {
                val photos = repository.uploadPhoto(image)
                val current = _uiState.value.profile ?: return@runAction _uiState.value
                _uiState.value.copy(profile = current.copy(photos = photos))
            }
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            runAction(isSaving = true) {
                val photos = repository.deletePhoto(photoId)
                val current = _uiState.value.profile ?: return@runAction _uiState.value
                _uiState.value.copy(profile = current.copy(photos = photos))
            }
        }
    }

    fun swipe(decision: SwipeDecision) {
        val candidate = _uiState.value.candidates.firstOrNull() ?: return
        viewModelScope.launch {
            runAction(isSwiping = true) {
                val result = repository.swipe(candidate.user.id, decision)
                if (result.isMatch && result.match != null) {
                    _effects.emit(DatingEffect.MatchCreated(result.match))
                }
                _uiState.value.copy(
                    candidates = repository.getDiscoverCandidates(),
                    matches = repository.getMatches()
                )
            }
        }
    }

    fun reviewPassedCandidates() {
        viewModelScope.launch {
            runAction {
                repository.resetPassedCandidates()
                refresh()
                _uiState.value
            }
        }
    }

    private suspend fun runAction(
        isSaving: Boolean = false,
        isSwiping: Boolean = false,
        action: suspend () -> DatingUiState
    ) {
        try {
            _uiState.value = _uiState.value.copy(isSaving = isSaving, isSwiping = isSwiping, error = null)
            _uiState.value = action().copy(isSaving = false, isSwiping = false)
        } catch (exception: IOException) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isSaving = false,
                isSwiping = false,
                error = "Cannot connect to the dating server"
            )
        } catch (exception: IllegalArgumentException) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isSaving = false,
                isSwiping = false,
                error = exception.message ?: "Dating action failed"
            )
        } catch (exception: IllegalStateException) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isSaving = false,
                isSwiping = false,
                error = exception.message ?: "Dating action failed"
            )
        }
    }
}
