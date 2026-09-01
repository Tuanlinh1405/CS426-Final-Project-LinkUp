package com.example.linkup.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val emailOrUsername: String,
    val password: String
)
