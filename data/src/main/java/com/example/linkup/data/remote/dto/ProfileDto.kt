package com.example.linkup.data.remote.dto

import kotlinx.serialization.Serializable

/** Mirror of the backend `ProfileResponse`. */
@Serializable
data class ProfileDto(
    val id: String,
    val username: String,
    val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val location: String? = null,
    val website: String? = null,
    val birthdate: String? = null,
    val gender: String? = null,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val postCount: Int = 0,
    val friendCount: Int = 0,
    val joinedAt: String = "",
    val isMe: Boolean = false,
    val isFollowing: Boolean = false,
    val isFollowedBy: Boolean = false,
    val friendshipStatus: String = "NONE",
    val mutualFriendCount: Int = 0
)

/**
 * Partial update. A null field is left untouched by the server; an empty string
 * clears an optional field.
 */
@Serializable
data class UpdateProfileRequestDto(
    val fullName: String? = null,
    val username: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val website: String? = null,
    val birthdate: String? = null,
    val gender: String? = null
)

@Serializable
data class MediaUploadResponseDto(
    val url: String,
    val key: String = "",
    val size: Long = 0,
    val contentType: String = ""
)

@Serializable
data class FollowStateDto(
    val isFollowing: Boolean = false,
    val followerCount: Int = 0
)

/** Error envelope returned by the backend for 4xx responses. */
@Serializable
data class ApiErrorDto(
    val message: String = "Something went wrong",
    val fieldErrors: Map<String, String> = emptyMap()
)

/** A person in a list: search results, followers, following. */
@Serializable
data class UserSummaryDto(
    val id: String,
    val username: String,
    val fullName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val isMe: Boolean = false,
    val isFollowing: Boolean = false,
    val friendshipStatus: String = "NONE",
    val mutualFriendCount: Int = 0
)

@Serializable
data class UserSummaryPageDto(
    val items: List<UserSummaryDto> = emptyList(),
    val nextCursor: String? = null,
    val total: Int = 0
)

/** Returned by every friend action so the caller can re-render immediately. */
@Serializable
data class FriendshipStateDto(
    val status: String = "NONE",
    val friendCount: Int = 0,
    val mutualFriendCount: Int = 0,
    val incomingRequestCount: Int = 0
)
