package com.linkup.repository

import com.linkup.database.DatabaseFactory.dbQuery
import com.linkup.database.PostEntity
import com.linkup.model.PostDto

class FeedRepository {
    suspend fun getFeed(): List<PostDto> = dbQuery {
        val posts = PostEntity.all().map { post ->
            val author = post.author
            val name = author.fullName ?: author.username
            val initials = if (name.isNotBlank()) name.take(2).uppercase() else "LU"
            PostDto(
                id = post.id.value.toString(),
                authorId = author.id.value.toString(),
                authorName = name,
                authorUsername = "@${author.username}",
                authorInitials = initials,
                time = "2h",
                content = post.content ?: "",
                mediaLabel = null,
                likes = 12,
                comments = 4,
                liked = false
            )
        }
        
        if (posts.isEmpty()) {
            // Return a dummy post if the database is empty so we can verify the API works
            listOf(
                PostDto(
                    id = "dummy-1",
                    authorId = "system",
                    authorName = "System Admin",
                    authorUsername = "@admin",
                    authorInitials = "SA",
                    time = "Just now",
                    content = "This is a post directly from the Ktor Backend! PostgreSQL currently has 0 posts.",
                    likes = 999,
                    comments = 999
                )
            )
        } else {
            posts
        }
    }
}
