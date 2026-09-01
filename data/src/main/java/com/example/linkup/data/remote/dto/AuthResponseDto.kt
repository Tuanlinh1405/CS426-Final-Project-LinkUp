package com.example.linkup.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponseDto(
    val user: UserDto,
    val token: String
)
