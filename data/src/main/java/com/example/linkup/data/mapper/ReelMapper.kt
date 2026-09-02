package com.example.linkup.data.mapper

import com.example.linkup.data.model.Reel
import com.example.linkup.data.model.User
import com.example.linkup.data.remote.dto.ReelDto

fun ReelDto.toDomain(): Reel {
    val author = User(
        id = authorId,
        name = authorName,
        username = authorUsername,
        initials = authorInitials
    )
    return Reel(
        id = id,
        author = author,
        caption = caption ?: "",
        videoUrl = videoUrl,
        thumbnailUrl = thumbnailUrl,
        likes = likes,
        comments = comments
    )
}
