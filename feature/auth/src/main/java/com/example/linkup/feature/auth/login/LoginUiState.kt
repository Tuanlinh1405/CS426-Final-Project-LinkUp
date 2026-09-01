package com.example.linkup.feature.auth.login

import com.example.linkup.data.model.AuthResponse

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val response: AuthResponse) : LoginUiState
    data class Error(val message: String) : LoginUiState
}
