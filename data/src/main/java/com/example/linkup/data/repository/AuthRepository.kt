package com.example.linkup.data.repository

import com.example.linkup.data.model.AuthResponse
import com.example.linkup.data.model.LoginRequest
import com.example.linkup.data.model.RegisterRequest
import com.example.linkup.data.network.AuthApiService
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

interface AuthRepository {
    suspend fun login(emailOrUsername: String, password: String): Result<AuthResponse>
    suspend fun register(email: String, username: String, password: String, fullName: String?): Result<AuthResponse>
}

class AuthRepositoryImpl : AuthRepository {
    private val baseUrl = "http://10.0.2.2:8080/" // Localhost for Android Emulator

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(AuthApiService::class.java)

    override suspend fun login(emailOrUsername: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.login(LoginRequest(emailOrUsername, password))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Unknown login error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(email: String, username: String, password: String, fullName: String?): Result<AuthResponse> {
        return try {
            val response = api.register(RegisterRequest(email, username, password, fullName))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Unknown registration error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
