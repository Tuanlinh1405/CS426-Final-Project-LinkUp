package com.example.linkup.data.search

import com.example.linkup.data.model.AuthResponse
import com.example.linkup.data.model.UserResponse
import com.example.linkup.data.network.ApiClient
import com.example.linkup.data.network.AuthSession
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SearchRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: SearchRepository

    @Before fun setup() {
        server = MockWebServer(); server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(ApiClient.client)
            .addConverterFactory(ApiClient.json.asConverterFactory("application/json".toMediaType())).build().create(SearchApi::class.java)
        repository = SearchRepositoryImpl(api)
        AuthSession.set(AuthResponse(UserResponse("viewer", "viewer@test.invalid", "viewer", "Viewer", "now"), "search-token"))
    }

    @After fun cleanup() { AuthSession.clear(); server.shutdown() }

    @Test fun `search sends type cursor and bearer token`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"posts":[{"id":"p1","author":{"id":"u1","username":"alice","name":"Alice"},"content":"football","createdAt":"2026-01-01T00:00:00Z"}],"nextCursor":"20"}""",
        ))
        val page = repository.search("football", "posts", "10")
        assertEquals("p1", page.posts.single().id)
        val request = server.takeRequest()
        assertEquals("Bearer search-token", request.getHeader("Authorization"))
        assertEquals("football", request.requestUrl!!.queryParameter("q"))
        assertEquals("posts", request.requestUrl!!.queryParameter("type"))
        assertEquals("10", request.requestUrl!!.queryParameter("cursor"))
    }
}
