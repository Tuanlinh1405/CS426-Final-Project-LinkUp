package com.example.linkup.feature.auth.register

import com.example.linkup.data.model.AuthResponse

sealed interface RegisterUiState {
    data object Idle : RegisterUiState
    data object Loading : RegisterUiState
    data class Success(val response: AuthResponse) : RegisterUiState
    data class Error(val message: String) : RegisterUiState
}
