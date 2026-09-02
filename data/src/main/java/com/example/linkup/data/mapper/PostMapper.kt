package com.example.linkup.data.mapper

import com.example.linkup.data.model.Post
import com.example.linkup.data.model.User
import com.example.linkup.data.remote.dto.PostDto

fun PostDto.toDomain(): Post {
    val author = User(
        id = authorId,
        name = authorName,
        username = authorUsername,
        initials = authorInitials
    )
    return Post(
        id = id,
        author = author,
        time = time,
        content = content,
        mediaLabel = mediaLabel,
        likes = likes,
        comments = comments,
        liked = liked
    )
}
