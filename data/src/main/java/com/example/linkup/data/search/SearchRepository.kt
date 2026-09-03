package com.example.linkup.data.search

import com.example.linkup.data.network.ApiClient
import kotlinx.serialization.decodeFromString
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SearchApi {
    @GET("search") suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): Response<SearchResults>
}

interface SearchRepository {
    suspend fun search(query: String, type: String = "all", cursor: String? = null): SearchResults
}

class SearchRepositoryImpl(private val api: SearchApi = ApiClient.retrofit.create(SearchApi::class.java)) : SearchRepository {
    override suspend fun search(query: String, type: String, cursor: String?): SearchResults =
        api.search(query, type, cursor).bodyOrThrow()

    private fun Response<SearchResults>.bodyOrThrow(): SearchResults {
        if (isSuccessful) return body() ?: throw SearchApiException(502, "Search returned an empty response.")
        val message = errorBody()?.use { body ->
            runCatching { ApiClient.json.decodeFromString<SearchError>(body.string()).message }.getOrNull()
        } ?: "Search failed (${code()})."
        throw SearchApiException(code(), message)
    }
}
