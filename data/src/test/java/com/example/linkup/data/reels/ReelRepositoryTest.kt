package com.example.linkup.data.reels

import com.example.linkup.data.model.AuthResponse
import com.example.linkup.data.model.UserResponse
import com.example.linkup.data.network.ApiClient
import com.example.linkup.data.network.AuthSession
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class ReelRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: ReelRepositoryImpl
    @Before fun setup() {
        server = MockWebServer(); server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(ApiClient.client)
            .addConverterFactory(ApiClient.json.asConverterFactory("application/json".toMediaType())).build().create(ReelApi::class.java)
        repository = ReelRepositoryImpl(api)
        AuthSession.set(AuthResponse(UserResponse("viewer", "test@example.test", "viewer", null, "now"), "test-token"))
    }
    @After fun cleanup() { repository.close(); AuthSession.clear(); server.shutdown() }
    @Test fun `feed sends token and cursor and deserializes server response`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"items":[],"nextCursor":"next-page"}"""))
        val page = repository.feed("session:15", null)
        assertEquals("next-page", page.nextCursor)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
        assertEquals("session:15", request.requestUrl!!.queryParameter("cursor"))
    }
    @Test fun `server failures are not silently treated as success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setHeader("Content-Type", "application/json").setBody("""{"message":"Only the author can delete this reel."}"""))
        try { repository.delete("other-reel"); fail("Expected an API error") }
        catch (error: ReelApiException) { assertEquals(403, error.status); assertTrue(error.message!!.contains("author")) }
        assertEquals("DELETE", server.takeRequest().method)
    }
}
