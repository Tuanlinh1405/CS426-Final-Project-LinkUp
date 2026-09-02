package com.example.linkup.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PostDto(
    @SerialName("id") val id: String,
    @SerialName("authorId") val authorId: String,
    @SerialName("authorName") val authorName: String,
    @SerialName("authorUsername") val authorUsername: String,
    @SerialName("authorInitials") val authorInitials: String,
    @SerialName("time") val time: String,
    @SerialName("content") val content: String,
    @SerialName("mediaLabel") val mediaLabel: String? = null,
    @SerialName("likes") val likes: Int = 0,
    @SerialName("comments") val comments: Int = 0,
    @SerialName("liked") val liked: Boolean = false
)
