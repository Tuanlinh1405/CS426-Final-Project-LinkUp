package com.example.linkup.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(emailOrUsername: String, password: String) {
        if (emailOrUsername.isBlank() || password.length < 4) {
            _uiState.value = LoginUiState.Error("Please enter a valid account")
            return
        }

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            authRepository.login(emailOrUsername, password)
                .onSuccess { response ->
                    _uiState.value = LoginUiState.Success(response)
                }
                .onFailure { exception ->
                    _uiState.value = LoginUiState.Error(exception.message ?: "Login failed")
                }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}
