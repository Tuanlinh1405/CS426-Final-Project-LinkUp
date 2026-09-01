package com.example.linkup.data.model

data class AuthResult(
    val user: AuthUser,
    val token: String
)

data class AuthUser(
    val id: String,
    val email: String,
    val username: String,
    val fullName: String?,
    val createdAt: String? = null
)

// Legacy compatibility aliases
typealias LoginRequest = com.example.linkup.data.remote.dto.LoginRequestDto
typealias RegisterRequest = com.example.linkup.data.remote.dto.RegisterRequestDto
typealias AuthResponse = AuthResult
typealias UserResponse = AuthUser
