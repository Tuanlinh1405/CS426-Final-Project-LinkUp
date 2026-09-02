package com.example.linkup.data.repository

import com.example.linkup.data.model.*
import com.example.linkup.data.network.AuthApiService
import com.example.linkup.data.network.AuthSession
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class AuthSessionTest {
    private val response = AuthResponse(UserResponse("test-user", "test@example.test", "tester", "Tester", "2026-08-31"), "test-token")
    @After fun clear() { AuthSession.clear() }
    @Test fun `successful login keeps token for Reels and logout clears it`() = runBlocking {
        val api = object : AuthApiService {
            override suspend fun login(request: LoginRequest) = Response.success(response)
            override suspend fun register(request: RegisterRequest) = Response.success(response)
        }
        assertTrue(AuthRepositoryImpl(api).login("tester", "not-a-real-password").isSuccess)
        assertEquals(response, AuthSession.current)
        AuthSession.clear(); assertNull(AuthSession.current)
    }
    @Test fun `failed login is a real failure without creating a session`() = runBlocking {
        val api = object : AuthApiService {
            override suspend fun login(request: LoginRequest): Response<AuthResponse> = Response.error(401, "Invalid account".toResponseBody())
            override suspend fun register(request: RegisterRequest): Response<AuthResponse> = Response.error(409, "Already exists".toResponseBody())
        }
        AuthSession.clear()
        assertTrue(AuthRepositoryImpl(api).login("tester", "wrong").isFailure)
        assertNull(AuthSession.current)
    }
}
