package com.example.linkup.data.reels

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ReelApi {
    @GET("reels") suspend fun feed(@Query("cursor") cursor: String?, @Query("authorId") author: String?): Response<ReelPage>
    @GET("reels/{id}") suspend fun get(@Path("id") id: String): Response<Reel>
    @Multipart @POST("reels") suspend fun upload(@Part("id") id: RequestBody, @Part("caption") caption: RequestBody, @Part video: MultipartBody.Part, @Part thumbnail: MultipartBody.Part?): Response<Reel>
    @PUT("reels/{id}/reaction") suspend fun like(@Path("id") id: String): Response<Reel>
    @DELETE("reels/{id}/reaction") suspend fun unlike(@Path("id") id: String): Response<Reel>
    @DELETE("reels/{id}") suspend fun delete(@Path("id") id: String): Response<Unit>
    @PUT("reels/{id}/hidden") suspend fun hide(@Path("id") id: String): Response<Unit>
    @DELETE("reels/{id}/hidden") suspend fun unhide(@Path("id") id: String): Response<Unit>
    @GET("reels/{id}/comments") suspend fun comments(@Path("id") id: String, @Query("cursor") cursor: String?): Response<CommentPage>
    @POST("reels/{id}/comments") suspend fun comment(@Path("id") id: String, @Body comment: AddComment): Response<ReelComment>
    @DELETE("reels/{id}/comments/{commentId}") suspend fun deleteComment(@Path("id") id: String, @Path("commentId") commentId: String): Response<Unit>
    @POST("reels/{id}/events") suspend fun watch(@Path("id") id: String, @Body event: WatchEvent): Response<Unit>
}
