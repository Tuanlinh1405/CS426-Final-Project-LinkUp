package com.example.linkup.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val username: String,
    val fullName: String? = null,
    val createdAt: String? = null
)
