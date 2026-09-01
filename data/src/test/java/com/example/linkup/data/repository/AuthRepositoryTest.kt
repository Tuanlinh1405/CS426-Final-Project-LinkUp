package com.example.linkup.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.linkup.data.local.datastore.AuthTokenDataStore
import com.example.linkup.data.remote.api.AuthApiService
import com.example.linkup.data.remote.dto.AuthResponseDto
import com.example.linkup.data.remote.dto.LoginRequestDto
import com.example.linkup.data.remote.dto.RegisterRequestDto
import com.example.linkup.data.remote.dto.UserDto
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AuthRepositoryTest {

    private lateinit var authRepository: AuthRepository

    @Before
    fun setup() {
        val fakePrefsDataStore = object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                return transform(emptyPreferences())
            }
        }
        val fakeAuthTokenDataStore = AuthTokenDataStore(fakePrefsDataStore)
        val fakeApi = FakeAuthApiService()
        authRepository = AuthRepositoryImpl(fakeApi, fakeAuthTokenDataStore)
    }

    @Test
    fun testLoginSuccess() = runBlocking {
        val result = authRepository.login("testuser", "password")
        assertTrue("Login should succeed", result.isSuccess)
        assertEquals("fake_jwt_token", result.getOrNull()?.token)
        assertEquals("testuser", result.getOrNull()?.user?.username)
    }

    @Test
    fun testRegisterSuccess() = runBlocking {
        val result = authRepository.register("test@example.com", "testuser", "password", "Test User")
        assertTrue("Register should succeed", result.isSuccess)
        assertEquals("fake_jwt_token", result.getOrNull()?.token)
        assertEquals("Test User", result.getOrNull()?.user?.fullName)
    }
}

private class FakeAuthApiService : AuthApiService {
    override suspend fun login(request: LoginRequestDto): Response<AuthResponseDto> {
        val userDto = UserDto("u1", "test@example.com", request.emailOrUsername, "Test User", "2026-01-01")
        val responseDto = AuthResponseDto(userDto, "fake_jwt_token")
        return Response.success(responseDto)
    }

    override suspend fun register(request: RegisterRequestDto): Response<AuthResponseDto> {
        val userDto = UserDto("u1", request.email, request.username, request.fullName, "2026-01-01")
        val responseDto = AuthResponseDto(userDto, "fake_jwt_token")
        return Response.success(responseDto)
    }
}
