package com.example.linkup.data.repository

import com.example.linkup.data.mapper.toDomain
import com.example.linkup.data.model.ChatMessage
import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.NotificationItem
import com.example.linkup.data.model.Post
import com.example.linkup.data.model.Reel
import com.example.linkup.data.model.User
import com.example.linkup.data.remote.api.FeedApiService
import com.example.linkup.data.remote.api.ReelsApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkLinkUpRepository @Inject constructor(
    private val reelsApiService: ReelsApiService,
    private val feedApiService: FeedApiService
) : LinkUpRepository {

    companion object {
        fun create(baseUrl: String = "http://10.0.2.2:8081/"): NetworkLinkUpRepository {
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
            val reelsApi = retrofit.create(ReelsApiService::class.java)
            val feedApi = retrofit.create(FeedApiService::class.java)
            return NetworkLinkUpRepository(reelsApi, feedApi)
        }
    }

    private val fakeRepo = FakeLinkUpRepository()
    private var cachedReels: List<Reel>? = null
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

    suspend fun fetchFeedRemote(): List<Post> {
        return try {
            val response = feedApiService.getFeed()
            if (response.isSuccessful && response.body() != null) {
                val feed = response.body()!!.map { it.toDomain() }
                cachedFeed = feed
                return feed
            }
            fakeRepo.feed()
        } catch (e: Exception) {
            fakeRepo.feed()
        }
    }

    override fun reels(): List<Reel> {
        cachedReels?.let { return it }
        return try {
            runBlocking(Dispatchers.IO) {
                fetchReelsRemote()
            }
        } catch (e: Exception) {
            fakeRepo.reels()
        }
    }

    suspend fun fetchReelsRemote(): List<Reel> {
        return try {
            val response = reelsApiService.getReels()
            if (response.isSuccessful && response.body() != null) {
                val reels = response.body()!!.map { it.toDomain() }
                if (reels.isNotEmpty()) {
                    cachedReels = reels
                    return reels
                }
            }
            fakeRepo.reels()
        } catch (e: Exception) {
            fakeRepo.reels()
        }
    }

    override fun conversations(): List<Conversation> = fakeRepo.conversations()
    override fun messages(): List<ChatMessage> = fakeRepo.messages()
    override fun notifications(): List<NotificationItem> = fakeRepo.notifications()
    override fun createPost(content: String): Post = fakeRepo.createPost(content)
    override fun toggleLike(postId: String): List<Post> = fakeRepo.toggleLike(postId)
    override fun sendMessage(text: String): List<ChatMessage> = fakeRepo.sendMessage(text)
}
