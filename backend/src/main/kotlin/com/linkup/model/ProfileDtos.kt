package com.linkup.model

import kotlinx.serialization.Serializable

/**
 * A user's profile.
 *
 * [email] and [phone] are only populated when the caller is the owner ([isMe]);
 * they stay null on other people's profiles so contact details are not leaked.
 */
@Serializable
data class ProfileResponse(
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
    val joinedAt: String,
    val isMe: Boolean = false,
    val isFollowing: Boolean = false,
    /** True when they follow the viewer — drives the "Follows you" badge. */
    val isFollowedBy: Boolean = false,
    /** NONE | REQUEST_SENT | REQUEST_RECEIVED | FRIENDS, from the viewer's side. */
    val friendshipStatus: String = "NONE",
    val mutualFriendCount: Int = 0
)

/**
 * Partial profile update.
 *
 * A `null` field is left unchanged. An empty string clears an optional field
 * (bio, phone, location, website, birthdate, gender, fullName); [username] and
 * [email] are required fields and cannot be cleared.
 */
@Serializable
data class UpdateProfileRequest(
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

/** Returned by the avatar and cover upload endpoints. */
@Serializable
data class MediaUploadResponse(
    val url: String,
    val key: String,
    val size: Long,
    val contentType: String
)

/** Result of following or unfollowing someone. */
@Serializable
data class FollowStateResponse(
    val isFollowing: Boolean,
    val followerCount: Int
)

/** Uniform error envelope. [fieldErrors] maps a form field to its message. */
@Serializable
data class ApiError(
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap()
)

/** A person as they appear in a list: search results, followers, following. */
@Serializable
data class UserSummary(
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

/** Cursor-paged list of people. */
@Serializable
data class UserSummaryPage(
    val items: List<UserSummary>,
    val nextCursor: String? = null,
    val total: Int = 0
)

/** Result of any friend action, so the client can re-render without refetching. */
@Serializable
data class FriendshipStateResponse(
    val status: String,
    val friendCount: Int = 0,
    val mutualFriendCount: Int = 0,
    /** Pending requests waiting on the caller — drives the Requests tab badge. */
    val incomingRequestCount: Int = 0
)
