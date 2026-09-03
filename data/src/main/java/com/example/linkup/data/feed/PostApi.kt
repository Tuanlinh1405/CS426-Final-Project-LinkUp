package com.example.linkup.data.feed

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface PostApi {
    @GET("posts") suspend fun feed(@Query("cursor") cursor: String?): Response<FeedPage>
    @GET("posts/{id}") suspend fun get(@Path("id") id: String): Response<FeedPost>
    @Multipart @POST("posts") suspend fun create(
        @Part("id") id: RequestBody,
        @Part("content") content: RequestBody,
        @Part media: List<MultipartBody.Part>,
    ): Response<FeedPost>
    @PUT("posts/{id}/reaction") suspend fun like(@Path("id") id: String): Response<FeedPost>
    @DELETE("posts/{id}/reaction") suspend fun unlike(@Path("id") id: String): Response<FeedPost>
    @DELETE("posts/{id}") suspend fun delete(@Path("id") id: String): Response<Unit>
    @GET("posts/{id}/comments") suspend fun comments(@Path("id") id: String, @Query("cursor") cursor: String?): Response<FeedCommentPage>
    @POST("posts/{id}/comments") suspend fun comment(@Path("id") id: String, @Body body: AddFeedComment): Response<FeedComment>
    @PUT("posts/{id}/comments/{commentId}/reaction") suspend fun likeComment(@Path("id") id: String, @Path("commentId") commentId: String): Response<FeedComment>
    @DELETE("posts/{id}/comments/{commentId}/reaction") suspend fun unlikeComment(@Path("id") id: String, @Path("commentId") commentId: String): Response<FeedComment>
    @DELETE("posts/{id}/comments/{commentId}") suspend fun deleteComment(@Path("id") id: String, @Path("commentId") commentId: String): Response<Unit>
}
