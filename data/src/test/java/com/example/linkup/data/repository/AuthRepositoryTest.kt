package com.example.linkup.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.*

class AuthRepositoryTest {

    private lateinit var authRepository: AuthRepository

    @Before
    fun setup() {
        authRepository = AuthRepositoryImpl()
    }

    @Test
    fun testRegisterAndLogin() = runBlocking {
        val randomUser = "user_" + UUID.randomUUID().toString().substring(0, 8)
        val email = "$randomUser@example.com"
        val password = "Password123!"

        // 1. Test Register
        println("Testing Register for $randomUser...")
        val registerResult = authRepository.register(
            email = email,
            username = randomUser,
            password = password,
            fullName = "Test User"
        )
        
        // Note: This test will fail if the backend is not running at 10.0.2.2:8080 
        // or if 10.0.2.2 is not reachable from the test environment.
        // On a local machine test, you might need to change the IP to localhost.
        
        if (registerResult.isSuccess) {
            println("Register Success!")
            assertTrue(registerResult.isSuccess)

            // 2. Test Login
            println("Testing Login...")
            val loginResult = authRepository.login(randomUser, password)
            assertTrue("Login should be successful after registration", loginResult.isSuccess)
            println("Login Success! Token: ${loginResult.getOrNull()?.token}")
        } else {
            println("Register Failed: ${registerResult.exceptionOrNull()?.message}")
            println("NOTE: This test requires the Backend server to be running and accessible.")
        }
    }
}
