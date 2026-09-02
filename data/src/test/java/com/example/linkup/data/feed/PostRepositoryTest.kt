package com.example.linkup.data.feed

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

class PostRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: PostRepository
    @Before fun setup() {
        server = MockWebServer(); server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(ApiClient.client)
            .addConverterFactory(ApiClient.json.asConverterFactory("application/json".toMediaType())).build().create(PostApi::class.java)
        repository = PostRepositoryImpl(api)
        AuthSession.set(AuthResponse(UserResponse("viewer", "viewer@test.invalid", "viewer", "Viewer", "now"), "feed-token"))
    }
    @After fun cleanup() { AuthSession.clear(); server.shutdown() }
    @Test fun `feed sends auth cursor and reads media`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"items":[{"id":"p1","author":{"id":"u1","username":"alice","name":"Alice"},"content":"Hello","privacy":"PUBLIC","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z","media":[{"id":"m1","url":"media/m1","mimeType":"image/jpeg"}],"likeCount":1,"commentCount":2,"liked":true}],"nextCursor":"next"}"""))
        val page = repository.feed("cursor")
        assertEquals("m1", page.items.single().media.single().id)
        val request = server.takeRequest()
        assertEquals("Bearer feed-token", request.getHeader("Authorization"))
        assertEquals("cursor", request.requestUrl!!.queryParameter("cursor"))
    }
    @Test fun `api error is surfaced`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setHeader("Content-Type", "application/json").setBody("""{"message":"Only the author can delete this post."}"""))
        try { repository.delete("p1"); fail("Expected API error") }
        catch (error: FeedApiException) { assertEquals(403, error.status); assertTrue(error.message!!.contains("author")) }
    }
}
