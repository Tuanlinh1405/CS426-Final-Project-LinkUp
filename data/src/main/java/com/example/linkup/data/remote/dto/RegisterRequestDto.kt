package com.example.linkup.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val email: String,
    val username: String,
    val password: String,
    val fullName: String? = null
)
