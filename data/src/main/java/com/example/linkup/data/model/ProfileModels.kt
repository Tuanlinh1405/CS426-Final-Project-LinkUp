package com.example.linkup.data.model

/** A user profile as the UI consumes it. */
data class Profile(
    val id: String,
    val username: String,
    val fullName: String?,
    val email: String?,
    val phone: String?,
    val bio: String?,
    val avatarUrl: String?,
    val coverUrl: String?,
    val location: String?,
    val website: String?,
    val birthdate: String?,
    val gender: String?,
    val followerCount: Int,
    val followingCount: Int,
    val postCount: Int,
    val friendCount: Int,
    val joinedAt: String,
    val isMe: Boolean,
    val isFollowing: Boolean,
    val isFollowedBy: Boolean,
    val friendship: FriendshipStatus,
    val mutualFriendCount: Int
) {
    /** Falls back to the username so the header is never blank. */
    val displayName: String get() = fullName?.takeIf { it.isNotBlank() } ?: username

    val handle: String get() = "@$username"

    /** Up to two letters for the placeholder avatar. */
    val initials: String
        get() = displayName
            .split(' ', '.', '_')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { username.take(1).uppercase() }

    /** Host only, for a tidy link chip. */
    val websiteLabel: String?
        get() = website?.substringAfter("://")?.trimEnd('/')

    val hasContactDetails: Boolean
        get() = !email.isNullOrBlank() || !phone.isNullOrBlank() ||
            !location.isNullOrBlank() || !website.isNullOrBlank()
}

/**
 * A partial profile edit.
 *
 * Null means "leave as-is"; an empty string clears the field. Mirrors the
 * backend contract so the UI can send only what actually changed.
 */
data class ProfileUpdate(
    val fullName: String? = null,
    val username: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val website: String? = null,
    val birthdate: String? = null,
    val gender: String? = null
) {
    val isEmpty: Boolean
        get() = listOf(fullName, username, email, phone, bio, location, website, birthdate, gender)
            .all { it == null }
}

/** An image chosen on the device, already decoded and downscaled for upload. */
data class PickedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String
) {
    override fun equals(other: Any?): Boolean =
        other is PickedImage && fileName == other.fileName &&
            mimeType == other.mimeType && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int =
        31 * (31 * bytes.contentHashCode() + mimeType.hashCode()) + fileName.hashCode()
}

/**
 * A failed profile call.
 *
 * [fieldErrors] maps a form field name to the message the server rejected it with,
 * so the edit screen can mark the offending input instead of showing one banner.
 */
class ProfileException(
    override val message: String,
    val fieldErrors: Map<String, String> = emptyMap()
) : Exception(message)

/** A person as shown in a list row. */
data class UserSummary(
    val id: String,
    val username: String,
    val fullName: String?,
    val avatarUrl: String?,
    val bio: String?,
    val isMe: Boolean,
    val isFollowing: Boolean,
    val friendship: FriendshipStatus = FriendshipStatus.NONE,
    val mutualFriendCount: Int = 0
) {
    val displayName: String get() = fullName?.takeIf { it.isNotBlank() } ?: username

    val handle: String get() = "@$username"

    val initials: String
        get() = displayName
            .split(' ', '.', '_')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { username.take(1).uppercase() }
}

data class UserSummaryPage(
    val items: List<UserSummary>,
    val nextCursor: String?,
    val total: Int
)

/**
 * The relationship between the signed-in user and someone else.
 *
 * [UNKNOWN] keeps the app forward compatible if the backend ever adds a state
 * (blocked, for instance) that this build does not know about.
 */
enum class FriendshipStatus {
    NONE,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    FRIENDS,
    UNKNOWN;

    val isFriend: Boolean get() = this == FRIENDS

    companion object {
        fun from(raw: String?): FriendshipStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

/** Result of a friend action. */
data class FriendshipState(
    val status: FriendshipStatus,
    val friendCount: Int,
    val mutualFriendCount: Int,
    val incomingRequestCount: Int
)
