package com.example.linkup.data.mapper

import com.example.linkup.data.model.Post
import com.example.linkup.data.model.User
import com.example.linkup.data.remote.dto.PostDto

fun PostDto.toDomain(): Post {
    return Post(
        id = this.id,
        author = User(
            id = this.authorId,
            name = this.authorName,
            username = this.authorUsername,
            initials = this.authorInitials
        ),
        time = this.time,
        content = this.content,
        mediaLabel = this.mediaLabel,
        likes = this.likes,
        comments = this.comments,
        liked = this.liked
    )
}
