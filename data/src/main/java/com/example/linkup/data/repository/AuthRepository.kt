package com.example.linkup.data.repository

import com.example.linkup.data.model.AuthResponse
import com.example.linkup.data.model.LoginRequest
import com.example.linkup.data.model.RegisterRequest
import com.example.linkup.data.network.AuthApiService
import com.example.linkup.data.network.ApiClient
import com.example.linkup.data.network.AuthSession
import kotlinx.coroutines.CancellationException

interface AuthRepository {
    suspend fun login(emailOrUsername: String, password: String): Result<AuthResponse>
    suspend fun register(email: String, username: String, password: String, fullName: String?): Result<AuthResponse>
}

class AuthRepositoryImpl(private val api: AuthApiService = ApiClient.retrofit.create(AuthApiService::class.java)) : AuthRepository {

    override suspend fun login(emailOrUsername: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.login(LoginRequest(emailOrUsername, password))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.also(AuthSession::set))
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Unknown login error"))
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(email: String, username: String, password: String, fullName: String?): Result<AuthResponse> {
        return try {
            val response = api.register(RegisterRequest(email, username, password, fullName))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.also(AuthSession::set))
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Unknown registration error"))
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
