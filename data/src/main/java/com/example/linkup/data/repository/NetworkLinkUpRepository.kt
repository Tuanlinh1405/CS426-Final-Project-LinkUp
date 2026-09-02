package com.example.linkup.data.repository

import com.example.linkup.data.mapper.toDomain
import com.example.linkup.data.model.ChatMessage
import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.NotificationItem
import com.example.linkup.data.model.Post
import com.example.linkup.data.model.User
import com.example.linkup.data.remote.api.PostApiService
import com.example.linkup.data.remote.dto.CreatePostRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json

import com.example.linkup.data.local.datastore.AuthTokenDataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkLinkUpRepository @Inject constructor(
    private val postApiService: PostApiService,
    private val authTokenDataStore: AuthTokenDataStore
) : LinkUpRepository {

    companion object {
        fun create(baseUrl: String = "http://10.0.2.2:8081/", authTokenDataStore: AuthTokenDataStore): NetworkLinkUpRepository {
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }
            val okHttpClient = OkHttpClient.Builder().build()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            val postApi = retrofit.create(PostApiService::class.java)
            return NetworkLinkUpRepository(postApi, authTokenDataStore)
        }
    }

    private val fakeRepo = FakeLinkUpRepository()
    private var cachedFeed: List<Post>? = null

    override fun currentUser(): User = fakeRepo.currentUser()
    
    override fun feed(): List<Post> {
        cachedFeed?.let { return it }
        return try {
            runBlocking(Dispatchers.IO) {
                fetchFeedRemote()
            }
        } catch (e: Exception) {
            fakeRepo.feed()
        }
    }

    private suspend fun fetchFeedRemote(): List<Post> {
        return try {
            val response = postApiService.getFeed()
            if (response.isSuccessful && response.body() != null) {
                val feed = response.body()!!.map { it.toDomain() }
                if (feed.isNotEmpty()) {
                    cachedFeed = feed
                    return feed
                }
            }
            fakeRepo.feed()
        } catch (e: Exception) {
            fakeRepo.feed()
        }
    }

    override fun createPost(content: String): Post {
        return try {
            runBlocking(Dispatchers.IO) {
                val token = authTokenDataStore.getStoredToken() ?: return@runBlocking fakeRepo.createPost(content)
                val response = postApiService.createPost("Bearer $token", CreatePostRequest(content))
                if (response.isSuccessful && response.body() != null) {
                    val newPost = response.body()!!.toDomain()
                    cachedFeed = listOf(newPost) + (cachedFeed ?: emptyList())
                    newPost
                } else {
                    fakeRepo.createPost(content)
                }
            }
        } catch (e: Exception) {
            fakeRepo.createPost(content)
        }
    }

    override fun conversations(): List<Conversation> = fakeRepo.conversations()
    override fun messages(): List<ChatMessage> = fakeRepo.messages()
    override fun notifications(): List<NotificationItem> = fakeRepo.notifications()
    override fun toggleLike(postId: String): List<Post> = fakeRepo.toggleLike(postId)
    override fun sendMessage(text: String): List<ChatMessage> = fakeRepo.sendMessage(text)
}
