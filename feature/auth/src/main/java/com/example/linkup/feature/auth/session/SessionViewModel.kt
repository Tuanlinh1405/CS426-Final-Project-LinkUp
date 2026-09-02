package com.example.linkup.feature.auth.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.repository.AuthRepository
import com.example.linkup.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SessionState {
    /** Still deciding — the splash stays up. */
    data object Checking : SessionState

    data object SignedIn : SessionState

    data object SignedOut : SessionState
}

/**
 * Owns whether there is a usable session.
 *
 * The token already survives an app restart in DataStore; this is what actually reads
 * it at startup, and what clears it on logout. Without this, "Log out" only navigated
 * away while leaving the session — and the next person to open the app inherited it.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow<SessionState>(SessionState.Checking)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var checked = false

    /**
     * Restores a previous session if the stored token still works.
     *
     * A stored token is not proof of a valid session — it expires after 24 hours — so
     * it is verified with one real call before the user is let past the login screen.
     * A token that fails is cleared rather than left to 401 on every later screen.
     */
    fun check() {
        if (checked) return
        checked = true

        viewModelScope.launch {
            val token = authRepository.getStoredToken()
            if (token.isNullOrBlank()) {
                _state.value = SessionState.SignedOut
                return@launch
            }
            profileRepository.getMyProfile()
                .onSuccess { _state.value = SessionState.SignedIn }
                .onFailure {
                    authRepository.logout()
                    _state.value = SessionState.SignedOut
                }
        }
    }

    /** Called after a successful login or registration. */
    fun onSignedIn() {
        checked = true
        _state.value = SessionState.SignedIn
    }

    /** Clears the stored token, then reports back so callers can reset their own state. */
    fun logout(onCleared: () -> Unit = {}) {
        viewModelScope.launch {
            authRepository.logout()
            _state.value = SessionState.SignedOut
            onCleared()
        }
    }
}
