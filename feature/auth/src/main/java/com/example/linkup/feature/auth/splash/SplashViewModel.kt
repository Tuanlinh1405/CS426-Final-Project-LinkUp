package com.example.linkup.feature.auth.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SplashUiState {
    data object Loading : SplashUiState
    data object Authenticated : SplashUiState
    data object Unauthenticated : SplashUiState
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SplashUiState>(SplashUiState.Loading)
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        checkAuthStatus()
    }

    fun checkAuthStatus() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val token = authRepository.getStoredToken()
            val elapsedTime = System.currentTimeMillis() - startTime
            val minSplashDuration = 650L
            if (elapsedTime < minSplashDuration) {
                delay(minSplashDuration - elapsedTime)
            }
            if (!token.isNullOrBlank()) {
                _uiState.value = SplashUiState.Authenticated
            } else {
                _uiState.value = SplashUiState.Unauthenticated
            }
        }
    }
}
