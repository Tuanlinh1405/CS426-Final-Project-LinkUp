package com.example.linkup.data.remote.interceptor

import com.example.linkup.data.local.datastore.AuthTokenDataStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val authTokenDataStore: AuthTokenDataStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // Skip adding token for auth endpoints
        if (path.contains("/auth/login") || path.contains("/auth/register")) {
            return chain.proceed(originalRequest)
        }

        val token = runBlocking { authTokenDataStore.getStoredToken() }

        val requestBuilder = originalRequest.newBuilder()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}
