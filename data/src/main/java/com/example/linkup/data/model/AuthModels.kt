package com.example.linkup.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val emailOrUsername: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
    val fullName: String? = null
)

@Serializable
data class AuthResponse(
    val user: UserResponse,
    val token: String
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val username: String,
    val fullName: String?,
    val createdAt: String
)
