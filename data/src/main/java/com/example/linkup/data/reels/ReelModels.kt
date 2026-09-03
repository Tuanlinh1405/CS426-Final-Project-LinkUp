package com.example.linkup.data.reels

import kotlinx.serialization.Serializable

@Serializable data class ReelAuthor(val id: String, val username: String, val name: String, val avatarUrl: String? = null) {
    val initials: String get() = name.split(' ').filter(String::isNotBlank).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
}
@Serializable data class Reel(
    val id: String, val author: ReelAuthor, val caption: String, val videoUrl: String,
    val thumbnailUrl: String? = null, val durationMs: Long, val width: Int, val height: Int,
    val createdAt: String, val likeCount: Long, val commentCount: Long, val liked: Boolean,
)
@Serializable data class ReelPage(val items: List<Reel>, val nextCursor: String? = null)
@Serializable data class ReelComment(
    val id: String, val author: ReelAuthor, val content: String, val createdAt: String,
    val parentId: String? = null, val likeCount: Long = 0, val liked: Boolean = false,
    val replies: List<ReelComment> = emptyList(),
)
@Serializable data class CommentPage(val items: List<ReelComment>, val nextCursor: String? = null)
@Serializable data class AddComment(val id: String, val content: String, val parentId: String? = null)
@Serializable data class WatchEvent(val id: String, val watchedMs: Long, val reason: String = "HEARTBEAT")
@Serializable data class ReelApiError(val message: String)
class ReelApiException(val status: Int, message: String) : Exception(message)
