package com.example.linkup.data.network

import com.example.linkup.data.BuildConfig
import com.example.linkup.data.model.AuthResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** In-memory login session; restart requires sign-in. Never persist passwords or print tokens. */
object AuthSession {
    private val mutable = MutableStateFlow<AuthResponse?>(null)
    val state = mutable.asStateFlow()
    val current: AuthResponse? get() = mutable.value
    fun set(response: AuthResponse) { mutable.value = response }
    fun clear() { mutable.value = null }
}

object ApiClient {
    val baseUrl: String = BuildConfig.API_BASE_URL
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(5, TimeUnit.MINUTES).writeTimeout(5, TimeUnit.MINUTES)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
            AuthSession.current?.token?.let { request.header("Authorization", "Bearer $it") }
            chain.proceed(request.build())
        }
        .build()
    val retrofit: Retrofit = Retrofit.Builder().baseUrl(baseUrl).client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()

    fun mediaUrl(value: String): String = if (value.startsWith("http://") || value.startsWith("https://")) value else baseUrl + value.trimStart('/')
}
