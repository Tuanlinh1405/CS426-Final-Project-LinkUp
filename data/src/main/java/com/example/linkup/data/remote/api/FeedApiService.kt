package com.example.linkup.data.remote.api

import com.example.linkup.data.remote.dto.PostDto
import retrofit2.Response
import retrofit2.http.GET

interface FeedApiService {
    @GET("api/v1/feed")
    suspend fun getFeed(): Response<List<PostDto>>
}
