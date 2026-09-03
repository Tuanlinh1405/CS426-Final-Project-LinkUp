package com.linkup.database

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

/** 1. Core User & Auth */
class UserEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserEntity>(UsersTable)
    var email by UsersTable.email
    var username by UsersTable.username
    var passwordHash by UsersTable.passwordHash
    var fullName by UsersTable.fullName
    var birthdate by UsersTable.birthdate
    var gender by UsersTable.gender
    var createdAt by UsersTable.createdAt
    var updatedAt by UsersTable.updatedAt
}

class RefreshTokenEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<RefreshTokenEntity>(RefreshTokensTable)
    var user by UserEntity referencedOn RefreshTokensTable.userId
    var token by RefreshTokensTable.token
    var expiresAt by RefreshTokensTable.expiresAt
    var createdAt by RefreshTokensTable.createdAt
}

/** 2. Profiles & Following */
class ProfileEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ProfileEntity>(ProfilesTable)
    var bio by ProfilesTable.bio
    var avatarUrl by ProfilesTable.avatarUrl
    var coverUrl by ProfilesTable.coverUrl
    var followerCount by ProfilesTable.followerCount
    var followingCount by ProfilesTable.followingCount
    var updatedAt by ProfilesTable.updatedAt
    
    // In our schema, profile ID is same as user ID
    var user by UserEntity referencedOn ProfilesTable.id
}

/** 3. Media */
class MediaEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MediaEntity>(MediaTable)
    var owner by UserEntity referencedOn MediaTable.ownerId
    var storageKey by MediaTable.storageKey
    var mimeType by MediaTable.mimeType
    var fileSize by MediaTable.fileSize
    var width by MediaTable.width
    var height by MediaTable.height
    var createdAt by MediaTable.createdAt
}

/** 4. Social */
class PostEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<PostEntity>(PostsTable)
    var author by UserEntity referencedOn PostsTable.authorId
    var content by PostsTable.content
    var privacyLevel by PostsTable.privacyLevel
    var createdAt by PostsTable.createdAt
    var updatedAt by PostsTable.updatedAt
}

class CommentEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CommentEntity>(CommentsTable)
    var post by PostEntity referencedOn CommentsTable.postId
    var author by UserEntity referencedOn CommentsTable.authorId
    var content by CommentsTable.content
    var createdAt by CommentsTable.createdAt
}

/** 5. Reels */
class ReelEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ReelEntity>(ReelsTable)
    var author by UserEntity referencedOn ReelsTable.authorId
    var caption by ReelsTable.caption
    var videoUrl by ReelsTable.videoUrl
    var thumbnailUrl by ReelsTable.thumbnailUrl
    var duration by ReelsTable.duration
    var width by ReelsTable.width
    var height by ReelsTable.height
    var createdAt by ReelsTable.createdAt
}

/** 6. Chat */
class ConversationEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ConversationEntity>(ConversationsTable)
    var type by ConversationsTable.type
    var createdAt by ConversationsTable.createdAt
    var updatedAt by ConversationsTable.updatedAt
}

class MessageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<MessageEntity>(MessagesTable)
    var conversation by ConversationEntity referencedOn MessagesTable.conversationId
    var sender by UserEntity referencedOn MessagesTable.senderId
    var type by MessagesTable.type
    var textContent by MessagesTable.textContent
    var media by MediaEntity optionalReferencedOn MessagesTable.mediaId
    var createdAt by MessagesTable.createdAt
}

/** 7. Dating */
class DatingProfileEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DatingProfileEntity>(DatingProfilesTable)
    var user by UserEntity referencedOn DatingProfilesTable.userId
    var bio by DatingProfilesTable.bio
    var interests by DatingProfilesTable.interests
    var lookingFor by DatingProfilesTable.lookingFor
    var preferredGender by DatingProfilesTable.preferredGender
    var minAge by DatingProfilesTable.minAge
    var maxAge by DatingProfilesTable.maxAge
    var locationLat by DatingProfilesTable.locationLat
    var locationLng by DatingProfilesTable.locationLng
    var createdAt by DatingProfilesTable.createdAt
}

class DatingPhotoEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DatingPhotoEntity>(DatingPhotosTable)
    var datingProfile by DatingProfileEntity referencedOn DatingPhotosTable.datingProfileId
    var photoUrl by DatingPhotosTable.photoUrl
    var displayOrder by DatingPhotosTable.displayOrder
}

class DatingMatchEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DatingMatchEntity>(DatingMatchesTable)
    var user1 by UserEntity referencedOn DatingMatchesTable.user1Id
    var user2 by UserEntity referencedOn DatingMatchesTable.user2Id
    var createdAt by DatingMatchesTable.createdAt
}

/** 8. AI */
class AIConversationEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AIConversationEntity>(AIConversationsTable)
    var user by UserEntity referencedOn AIConversationsTable.userId
    var title by AIConversationsTable.title
    var createdAt by AIConversationsTable.createdAt
    var updatedAt by AIConversationsTable.updatedAt
}

class AIMessageEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AIMessageEntity>(AIMessagesTable)
    var aiConversation by AIConversationEntity referencedOn AIMessagesTable.aiConversationId
    var role by AIMessagesTable.role
    var content by AIMessagesTable.content
    var createdAt by AIMessagesTable.createdAt
}

/** 9. Notifications */
class NotificationEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<NotificationEntity>(NotificationsTable)
    var recipient by UserEntity referencedOn NotificationsTable.recipientId
    var actor by UserEntity referencedOn NotificationsTable.actorId
    var type by NotificationsTable.type
    var targetId by NotificationsTable.targetId
    var isRead by NotificationsTable.isRead
    var createdAt by NotificationsTable.createdAt
}
