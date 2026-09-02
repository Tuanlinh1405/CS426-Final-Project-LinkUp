package com.linkup.repository

import com.linkup.database.DatabaseFactory.dbQuery
import com.linkup.database.PostEntity
import com.linkup.database.UserEntity
import com.linkup.model.PostDto
import java.util.UUID

class PostRepository {
    
    suspend fun getFeed(): List<PostDto> = dbQuery {
        PostEntity.all().map { post ->
            mapToDto(post)
        }
    }

    suspend fun createPost(authorId: String, content: String, privacyLevel: String = "PUBLIC"): PostDto? = dbQuery {
        val user = UserEntity.findById(UUID.fromString(authorId)) ?: return@dbQuery null
        
        val newPost = PostEntity.new {
            this.author = user
            this.content = content
            this.privacyLevel = privacyLevel
        }
        
        mapToDto(newPost)
    }
    
    private fun mapToDto(post: PostEntity): PostDto {
        val author = post.author
        val name = author.fullName ?: author.username
        val initials = if (name.isNotBlank()) name.take(2).uppercase() else "LU"
        return PostDto(
            id = post.id.value.toString(),
            authorId = author.id.value.toString(),
            authorName = name,
            authorUsername = "@${author.username}",
            authorInitials = initials,
            time = "Just now",
            content = post.content ?: "",
            mediaLabel = null,
            likes = 0,
            comments = 0,
            liked = false
        )
    }
}
