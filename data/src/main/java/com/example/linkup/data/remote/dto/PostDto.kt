package com.example.linkup.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PostDto(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val authorInitials: String,
    val time: String,
    val content: String,
    val mediaLabel: String? = null,
    val likes: Int = 0,
    val comments: Int = 0,
    val liked: Boolean = false
)

@Serializable
data class CreatePostRequest(
    val content: String,
    val privacyLevel: String = "PUBLIC"
)
