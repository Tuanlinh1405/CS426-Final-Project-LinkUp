package com.example.linkup.data.repository

import com.example.linkup.data.model.AuthResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val tokenFlow: Flow<String?>
    suspend fun login(emailOrUsername: String, password: String): Result<AuthResult>
    suspend fun register(email: String, username: String, password: String, fullName: String?): Result<AuthResult>
    suspend fun logout()
    suspend fun getStoredToken(): String?
}
