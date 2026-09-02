package com.example.linkup.data.remote.api

import com.example.linkup.data.remote.dto.ReelDto
import retrofit2.Response
import retrofit2.http.GET

interface ReelsApiService {
    @GET("api/v1/reels")
    suspend fun getReels(): Response<List<ReelDto>>
}

