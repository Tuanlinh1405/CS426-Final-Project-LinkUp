package com.example.linkup.feature.auth.register

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
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun register(email: String, username: String, password: String, fullName: String?) {
        if (fullName.isNullOrBlank() || !email.contains("@") || password.length < 4) {
            _uiState.value = RegisterUiState.Error("Please fill in all fields correctly")
            return
        }

        _uiState.value = RegisterUiState.Loading
        viewModelScope.launch {
            authRepository.register(email, username, password, fullName)
                .onSuccess { response ->
                    _uiState.value = RegisterUiState.Success(response)
                }
                .onFailure { exception ->
                    _uiState.value = RegisterUiState.Error(exception.message ?: "Registration failed")
                }
        }
    }

    fun resetState() {
        _uiState.value = RegisterUiState.Idle
    }
}
