package com.example.linkup.data.mapper

import com.example.linkup.data.model.FriendshipState
import com.example.linkup.data.model.FriendshipStatus
import com.example.linkup.data.model.Profile
import com.example.linkup.data.model.ProfileUpdate
import com.example.linkup.data.model.UserSummary
import com.example.linkup.data.model.UserSummaryPage
import com.example.linkup.data.remote.dto.FriendshipStateDto
import com.example.linkup.data.remote.dto.ProfileDto
import com.example.linkup.data.remote.dto.UpdateProfileRequestDto
import com.example.linkup.data.remote.dto.UserSummaryDto
import com.example.linkup.data.remote.dto.UserSummaryPageDto

fun ProfileDto.toDomain(): Profile = Profile(
    id = id,
    username = username,
    fullName = fullName,
    email = email,
    phone = phone,
    bio = bio,
    avatarUrl = avatarUrl,
    coverUrl = coverUrl,
    location = location,
    website = website,
    birthdate = birthdate,
    gender = gender,
    followerCount = followerCount,
    followingCount = followingCount,
    postCount = postCount,
    friendCount = friendCount,
    joinedAt = joinedAt,
    isMe = isMe,
    isFollowing = isFollowing,
    isFollowedBy = isFollowedBy,
    friendship = FriendshipStatus.from(friendshipStatus),
    mutualFriendCount = mutualFriendCount
)

fun ProfileUpdate.toDto(): UpdateProfileRequestDto = UpdateProfileRequestDto(
    fullName = fullName,
    username = username,
    email = email,
    phone = phone,
    bio = bio,
    location = location,
    website = website,
    birthdate = birthdate,
    gender = gender
)

fun UserSummaryDto.toDomain(): UserSummary = UserSummary(
    id = id,
    username = username,
    fullName = fullName,
    avatarUrl = avatarUrl,
    bio = bio,
    isMe = isMe,
    isFollowing = isFollowing,
    friendship = FriendshipStatus.from(friendshipStatus),
    mutualFriendCount = mutualFriendCount
)

fun UserSummaryPageDto.toDomain(): UserSummaryPage = UserSummaryPage(
    items = items.map { it.toDomain() },
    nextCursor = nextCursor,
    total = total
)

fun FriendshipStateDto.toDomain(): FriendshipState = FriendshipState(
    status = FriendshipStatus.from(status),
    friendCount = friendCount,
    mutualFriendCount = mutualFriendCount,
    incomingRequestCount = incomingRequestCount
)
