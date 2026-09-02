package com.linkup.reels

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable data class ReelAuthor(val id: String, val username: String, val name: String, val avatarUrl: String? = null)
@Serializable data class ReelDto(
    val id: String, val author: ReelAuthor, val caption: String, val videoUrl: String,
    val thumbnailUrl: String? = null, val durationMs: Long, val width: Int, val height: Int,
    val createdAt: String, val likeCount: Long, val commentCount: Long, val liked: Boolean,
    @Transient val videoKey: String? = null,
    @Transient val thumbnailKey: String? = null,
    @Transient val storageBackend: String? = null,
)
@Serializable data class ReelPage(val items: List<ReelDto>, val nextCursor: String? = null)
@Serializable data class ReelCommentDto(val id: String, val author: ReelAuthor, val content: String, val createdAt: String)
@Serializable data class CommentPage(val items: List<ReelCommentDto>, val nextCursor: String? = null)
@Serializable data class AddComment(val id: String, val content: String)
@Serializable data class WatchEvent(val id: String, val watchedMs: Long, val reason: String = "HEARTBEAT")
@Serializable data class ApiError(val message: String)
class ReelFailure(val status: Int, override val message: String) : RuntimeException(message)
data class ReelAsset(val videoKey: String, val thumbnailKey: String?, val storageBackend: String, val fileSize: Long)
data class VideoMetadata(val durationMs: Long, val width: Int, val height: Int)
data class Candidate(val reel: ReelDto, val affinity: Double, val following: Boolean, val quality: Double, val seen: Boolean)
