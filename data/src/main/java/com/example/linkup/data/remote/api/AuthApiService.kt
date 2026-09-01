package com.example.linkup.data.remote.api

import com.example.linkup.data.remote.dto.AuthResponseDto
import com.example.linkup.data.remote.dto.LoginRequestDto
import com.example.linkup.data.remote.dto.RegisterRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<AuthResponseDto>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequestDto): Response<AuthResponseDto>
}
