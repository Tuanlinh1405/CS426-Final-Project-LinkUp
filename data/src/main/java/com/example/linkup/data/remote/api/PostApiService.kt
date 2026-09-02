package com.example.linkup.data.remote.api

import com.example.linkup.data.remote.dto.CreatePostRequest
import com.example.linkup.data.remote.dto.PostDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface PostApiService {
    @GET("api/v1/feed")
    suspend fun getFeed(): Response<List<PostDto>>

    @POST("api/v1/posts")
    suspend fun createPost(
        @Header("Authorization") token: String,
        @Body request: CreatePostRequest
    ): Response<PostDto>
}
