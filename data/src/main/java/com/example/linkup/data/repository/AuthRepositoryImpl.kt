package com.example.linkup.data.repository

import com.example.linkup.data.local.datastore.AuthTokenDataStore
import com.example.linkup.data.mapper.toDomain
import com.example.linkup.data.model.AuthResult
import com.example.linkup.data.remote.api.AuthApiService
import com.example.linkup.data.remote.dto.LoginRequestDto
import com.example.linkup.data.remote.dto.RegisterRequestDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApiService,
    private val authTokenDataStore: AuthTokenDataStore
) : AuthRepository {

    override val tokenFlow: Flow<String?> = authTokenDataStore.tokenFlow

    override suspend fun getStoredToken(): String? {
        return authTokenDataStore.getStoredToken()
    }

    override suspend fun login(emailOrUsername: String, password: String): Result<AuthResult> {
        return try {
            val response = api.login(LoginRequestDto(emailOrUsername, password))
            if (response.isSuccessful && response.body() != null) {
                val authResponseDto = response.body()!!
                val domainResult = authResponseDto.toDomain()
                authTokenDataStore.saveAuthData(
                    token = domainResult.token,
                    userId = domainResult.user.id,
                    email = domainResult.user.email,
                    username = domainResult.user.username,
                    fullName = domainResult.user.fullName
                )
                Result.success(domainResult)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Unknown login error"))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    override suspend fun register(email: String, username: String, password: String, fullName: String?): Result<AuthResult> {
        return try {
            val response = api.register(RegisterRequestDto(email, username, password, fullName))
            if (response.isSuccessful && response.body() != null) {
                val authResponseDto = response.body()!!
                val domainResult = authResponseDto.toDomain()
                authTokenDataStore.saveAuthData(
                    token = domainResult.token,
                    userId = domainResult.user.id,
                    email = domainResult.user.email,
                    username = domainResult.user.username,
                    fullName = domainResult.user.fullName
                )
                Result.success(domainResult)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Unknown registration error"))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    override suspend fun logout() {
        authTokenDataStore.clearAuthData()
    }

    private fun mapException(e: Exception): Exception {
        val msg = e.message ?: ""
        return when {
            e is java.net.SocketTimeoutException || msg.contains("timeout", ignoreCase = true) -> {
                Exception("Không thể kết nối đến server (Timeout). Vui lòng kiểm tra server backend tại http://10.0.2.2:8080")
            }
            e is java.net.ConnectException || e is java.net.UnknownHostException -> {
                Exception("Không thể kết nối đến server. Hãy đảm bảo server backend đang chạy.")
            }
            else -> e
        }
    }
}
