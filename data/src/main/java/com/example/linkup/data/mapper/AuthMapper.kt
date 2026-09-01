package com.example.linkup.data.mapper

import com.example.linkup.data.model.AuthResult
import com.example.linkup.data.model.AuthUser
import com.example.linkup.data.remote.dto.AuthResponseDto
import com.example.linkup.data.remote.dto.UserDto

fun UserDto.toDomain(): AuthUser {
    return AuthUser(
        id = id,
        email = email,
        username = username,
        fullName = fullName,
        createdAt = createdAt
    )
}

fun AuthResponseDto.toDomain(): AuthResult {
    return AuthResult(
        user = user.toDomain(),
        token = token
    )
}
