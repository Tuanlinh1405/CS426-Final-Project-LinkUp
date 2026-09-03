package com.example.linkup.data.search

import kotlinx.serialization.Serializable

@Serializable data class SearchPerson(
    val id: String,
    val username: String,
    val name: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val followerCount: Long = 0,
) {
    val initials: String get() = name.split(' ').filter(String::isNotBlank).take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
}

@Serializable data class SearchPost(
    val id: String,
    val author: SearchPerson,
    val content: String,
    val createdAt: String,
    val imageId: String? = null,
    val imageUrl: String? = null,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
)

@Serializable data class SearchReel(
    val id: String,
    val author: SearchPerson,
    val caption: String,
    val createdAt: String,
    val videoUrl: String,
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0,
    val likeCount: Long = 0,
    val commentCount: Long = 0,
)

@Serializable data class SearchResults(
    val people: List<SearchPerson> = emptyList(),
    val posts: List<SearchPost> = emptyList(),
    val reels: List<SearchReel> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable internal data class SearchError(val message: String)
class SearchApiException(val status: Int, message: String) : Exception(message)
