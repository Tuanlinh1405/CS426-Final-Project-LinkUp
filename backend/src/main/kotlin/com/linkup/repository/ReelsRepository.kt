package com.linkup.repository

import com.linkup.database.DatabaseFactory.dbQuery
import com.linkup.database.ReelEntity
import kotlinx.serialization.Serializable

@Serializable
data class ReelResponse(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val authorInitials: String,
    val caption: String? = null,
    val videoUrl: String,
    val thumbnailUrl: String? = null,
    val likes: Int = 0,
    val comments: Int = 0
)

class ReelsRepository {
    suspend fun getAllReels(): List<ReelResponse> = dbQuery {
        ReelEntity.all().map { reel ->
            val author = reel.author
            val name = author.fullName ?: author.username
            val initials = if (name.isNotBlank()) name.take(2).uppercase() else "LU"
            ReelResponse(
                id = reel.id.value.toString(),
                authorId = author.id.value.toString(),
                authorName = name,
                authorUsername = "@${author.username}",
                authorInitials = initials,
                caption = reel.caption,
                videoUrl = reel.videoUrl,
                thumbnailUrl = reel.thumbnailUrl
            )
        }
    }
}
