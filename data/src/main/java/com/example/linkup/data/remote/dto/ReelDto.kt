package com.example.linkup.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReelDto(
    @SerialName("id") val id: String,
    @SerialName("authorId") val authorId: String,
    @SerialName("authorName") val authorName: String,
    @SerialName("authorUsername") val authorUsername: String,
    @SerialName("authorInitials") val authorInitials: String,
    @SerialName("caption") val caption: String? = null,
    @SerialName("videoUrl") val videoUrl: String,
    @SerialName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerialName("likes") val likes: Int = 0,
    @SerialName("comments") val comments: Int = 0
)
