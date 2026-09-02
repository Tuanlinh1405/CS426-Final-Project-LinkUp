package com.linkup.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/** 1. Core User & Auth */
object UsersTable : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val username = varchar("username", 50).uniqueIndex()
    val passwordHash = text("password_hash")
    val fullName = varchar("full_name", 100).nullable()
    val birthdate = date("birthdate").nullable()
    val gender = varchar("gender", 20).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object RefreshTokensTable : UUIDTable("refresh_tokens") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val token = text("token")
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

/** 2. Profiles & Following */
object ProfilesTable : UUIDTable("profiles", "user_id") {
    val bio = text("bio").nullable()
    val avatarUrl = text("avatar_url").nullable()
    val coverUrl = text("cover_url").nullable()
    val followerCount = integer("follower_count").default(0)
    val followingCount = integer("following_count").default(0)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object FollowsTable : Table("follows") {
    val followerId = reference("follower_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val followingId = reference("following_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(followerId, followingId)
    
    init {
        check("cannot_follow_self") { followerId neq followingId }
    }
}

/** 3. Media Storage Metadata */
object MediaTable : UUIDTable("media") {
    val ownerId = reference("owner_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val storageKey = text("storage_key").uniqueIndex()
    val mimeType = varchar("mime_type", 100).nullable()
    val fileSize = long("file_size").nullable()
    val width = integer("width").nullable()
    val height = integer("height").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

/** 4. Social (Posts, Comments, Reactions) */
object PostsTable : UUIDTable("posts") {
    val authorId = reference("author_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val content = text("content").nullable()
    val privacyLevel = varchar("privacy_level", 20).default("PUBLIC")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object PostMediaTable : Table("post_media") {
    val postId = reference("post_id", PostsTable, onDelete = ReferenceOption.CASCADE)
    val mediaId = reference("media_id", MediaTable, onDelete = ReferenceOption.CASCADE)
    val displayOrder = integer("display_order").default(0)
    override val primaryKey = PrimaryKey(postId, mediaId)
}

object CommentsTable : UUIDTable("comments") {
    val postId = reference("post_id", PostsTable, onDelete = ReferenceOption.CASCADE)
    val authorId = reference("author_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val content = text("content")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

object PostReactionsTable : Table("post_reactions") {
    val postId = reference("post_id", PostsTable, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val type = varchar("type", 20).default("LIKE")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(postId, userId)
}

/** 5. Reels */
object ReelsTable : UUIDTable("reels") {
    val authorId = reference("author_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val caption = text("caption").nullable()
    val videoUrl = text("video_url")
    val thumbnailUrl = text("thumbnail_url").nullable()
    val duration = integer("duration").nullable()
    val width = integer("width").nullable()
    val height = integer("height").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

/** 6. Chat (Realtime) */
object ConversationsTable : UUIDTable("conversations") {
    val type = varchar("type", 20).default("DIRECT")
    val name = varchar("name", 100).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object ConversationMembersTable : Table("conversation_members") {
    val conversationId = reference("conversation_id", ConversationsTable, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val joinedAt = timestamp("joined_at").defaultExpression(CurrentTimestamp)
    val lastReadAt = timestamp("last_read_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(conversationId, userId)
}

object MessagesTable : UUIDTable("messages") {
    val conversationId = reference("conversation_id", ConversationsTable, onDelete = ReferenceOption.CASCADE)
    val senderId = reference("sender_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val type = varchar("type", 20).default("TEXT")
    val textContent = text("text_content").nullable()
    val mediaId = reference("media_id", MediaTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

object MessageReceiptsTable : Table("message_receipts") {
    val messageId = reference("message_id", MessagesTable, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val status = varchar("status", 20).default("SENT") // SENT, DELIVERED, SEEN
    val deliveredAt = timestamp("delivered_at").nullable()
    val readAt = timestamp("read_at").nullable()
    override val primaryKey = PrimaryKey(messageId, userId)
}

/** 7. Dating */
object DatingProfilesTable : UUIDTable("dating_profiles") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val bio = text("bio").nullable()
    val interests = text("interests").nullable()
    val preferredGender = varchar("preferred_gender", 20).nullable()
    val minAge = integer("min_age").nullable()
    val maxAge = integer("max_age").nullable()
    val locationLat = double("location_lat").nullable()
    val locationLng = double("location_lng").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

object DatingPhotosTable : UUIDTable("dating_photos") {
    val datingProfileId = reference("dating_profile_id", DatingProfilesTable, onDelete = ReferenceOption.CASCADE)
    val photoUrl = text("photo_url")
    val displayOrder = integer("display_order").default(0)
}

object DatingSwipesTable : Table("dating_swipes") {
    val swiperId = reference("swiper_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val targetId = reference("target_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val direction = varchar("direction", 10)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(swiperId, targetId)
}

object DatingMatchesTable : UUIDTable("dating_matches") {
    val user1Id = reference("user1_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val user2Id = reference("user2_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    
    init {
        uniqueIndex(user1Id, user2Id)
    }
}

/** 8. AI Assistant */
object AIConversationsTable : UUIDTable("ai_conversations") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val title = text("title").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

object AIMessagesTable : UUIDTable("ai_messages") {
    val aiConversationId = reference("ai_conversation_id", AIConversationsTable, onDelete = ReferenceOption.CASCADE)
    val role = varchar("role", 20)
    val content = text("content")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

/** 9. Notifications */
object NotificationsTable : UUIDTable("notifications") {
    val recipientId = reference("recipient_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val actorId = reference("actor_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val type = varchar("type", 50)
    val targetId = uuid("target_id").nullable()
    val isRead = bool("is_read").default(false)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
