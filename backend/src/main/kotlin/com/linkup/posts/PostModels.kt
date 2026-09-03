package com.linkup.posts

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable data class PostAuthor(val id: String, val username: String, val name: String, val avatarUrl: String? = null)
@Serializable data class PostMediaDto(
    val id: String,
    val url: String,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    @Transient val storageKey: String? = null,
)
@Serializable data class PostDto(
    val id: String,
    val author: PostAuthor,
    val content: String,
    val privacy: String,
    val createdAt: String,
    val updatedAt: String,
    val media: List<PostMediaDto>,
    val likeCount: Long,
    val commentCount: Long,
    val liked: Boolean,
)
@Serializable data class PostPage(val items: List<PostDto>, val nextCursor: String? = null)
@Serializable data class PostCommentDto(
    val id: String,
    val author: PostAuthor,
    val content: String,
    val createdAt: String,
    val parentId: String? = null,
    val likeCount: Long = 0,
    val liked: Boolean = false,
    val replies: List<PostCommentDto> = emptyList(),
)
@Serializable data class PostCommentPage(val items: List<PostCommentDto>, val nextCursor: String? = null)
@Serializable data class AddPostComment(val id: String, val content: String, val parentId: String? = null)
@Serializable data class PostApiError(val message: String)

class PostFailure(val status: Int, override val message: String) : RuntimeException(message)
data class NewPostMedia(val id: java.util.UUID, val storageKey: String, val mimeType: String, val fileSize: Long)
data class StoredPostMedia(val id: java.util.UUID, val storageKey: String, val mimeType: String, val fileSize: Long)
