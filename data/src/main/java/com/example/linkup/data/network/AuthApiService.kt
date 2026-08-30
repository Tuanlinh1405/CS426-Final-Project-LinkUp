package com.example.linkup.data.network

import com.example.linkup.data.model.AuthResponse
import com.example.linkup.data.model.LoginRequest
import com.example.linkup.data.model.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>
}
