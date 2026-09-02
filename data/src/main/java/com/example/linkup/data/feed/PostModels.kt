package com.example.linkup.data.feed

import kotlinx.serialization.Serializable

@Serializable data class FeedAuthor(val id: String, val username: String, val name: String, val avatarUrl: String? = null) {
    val initials: String get() = name.split(' ').filter(String::isNotBlank).take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
}
@Serializable data class FeedMedia(
    val id: String,
    val url: String,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
)
@Serializable data class FeedPost(
    val id: String,
    val author: FeedAuthor,
    val content: String,
    val privacy: String,
    val createdAt: String,
    val updatedAt: String,
    val media: List<FeedMedia> = emptyList(),
    val likeCount: Long = 0,
    val commentCount: Long = 0,
    val liked: Boolean = false,
)
@Serializable data class FeedPage(val items: List<FeedPost>, val nextCursor: String? = null)
@Serializable data class FeedComment(val id: String, val author: FeedAuthor, val content: String, val createdAt: String)
@Serializable data class FeedCommentPage(val items: List<FeedComment>, val nextCursor: String? = null)
@Serializable data class AddFeedComment(val id: String, val content: String)
@Serializable data class FeedApiError(val message: String)

class FeedApiException(val status: Int, message: String) : Exception(message)
