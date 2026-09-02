package com.linkup.repository

import com.linkup.database.DatabaseFactory.dbQuery
import com.linkup.database.FollowsTable
import com.linkup.database.PostsTable
import com.linkup.database.ProfilesTable
import com.linkup.database.UserEntity
import com.linkup.database.UsersTable
import com.linkup.model.FollowStateResponse
import com.linkup.model.ProfileResponse
import com.linkup.model.UpdateProfileRequest
import com.linkup.model.UserSummary
import com.linkup.model.UserSummaryPage
import com.linkup.service.FieldError
import com.linkup.service.ProfileValidationException
import com.linkup.service.ProfileValidator
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

/** Raised when an update collides with another account's username or email. */
class ProfileConflictException(val error: FieldError) : RuntimeException(error.message)

class ProfileRepository {

    /**
     * Loads [targetId]'s profile as seen by [viewerId].
     *
     * Contact details are included only when the viewer owns the profile.
     * Returns null when the user does not exist.
     */
    suspend fun getProfile(targetId: UUID, viewerId: UUID?): ProfileResponse? = dbQuery {
        val user = UserEntity.findById(targetId) ?: return@dbQuery null
        val profileRow = profileRow(targetId) ?: run {
            createProfileRow(targetId)
            profileRow(targetId)
        }
        val isMe = viewerId != null && viewerId == targetId

        ProfileResponse(
            id = targetId.toString(),
            username = user.username,
            fullName = user.fullName,
            email = if (isMe) user.email else null,
            phone = if (isMe) user.phone else null,
            bio = profileRow?.get(ProfilesTable.bio),
            avatarUrl = profileRow?.get(ProfilesTable.avatarUrl),
            coverUrl = profileRow?.get(ProfilesTable.coverUrl),
            location = profileRow?.get(ProfilesTable.location),
            website = profileRow?.get(ProfilesTable.website),
            birthdate = user.birthdate?.toString(),
            gender = user.gender,
            followerCount = countFollowers(targetId),
            followingCount = countFollowing(targetId),
            postCount = countPosts(targetId),
            joinedAt = user.createdAt.toString(),
            isMe = isMe,
            isFollowing = viewerId != null && !isMe && isFollowing(viewerId, targetId)
        )
    }

    /** Resolves a `@handle` or raw username to a user id. */
    suspend fun findIdByUsername(username: String): UUID? = dbQuery {
        UserEntity.find { UsersTable.username eq username.removePrefix("@").lowercase() }
            .singleOrNull()?.id?.value
    }

    /**
     * Applies a partial update, then returns the refreshed profile.
     *
     * Returns null when the user no longer exists.
     *
     * @throws ProfileValidationException when a field is malformed.
     * @throws ProfileConflictException when username or email is taken.
     */
    suspend fun updateProfile(userId: UUID, request: UpdateProfileRequest): ProfileResponse? {
        val applied = dbQuery {
            val user = UserEntity.findById(userId) ?: return@dbQuery false
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

            // Validate everything before writing anything, so a rejected field
            // never leaves the row half-updated.
            val newUsername = request.username?.let { ProfileValidator.username(it).getOrThrow() }
            val newEmail = request.email?.let { ProfileValidator.email(it).getOrThrow() }
            val newPhone = request.phone?.let { ProfileValidator.phone(it).getOrThrow() }
            val newFullName = request.fullName?.let { ProfileValidator.fullName(it).getOrThrow() }
            val newBio = request.bio?.let { ProfileValidator.bio(it).getOrThrow() }
            val newLocation = request.location?.let { ProfileValidator.location(it).getOrThrow() }
            val newWebsite = request.website?.let { ProfileValidator.website(it).getOrThrow() }
            val newGender = request.gender?.let { ProfileValidator.gender(it).getOrThrow() }
            val newBirthdate: LocalDate? =
                request.birthdate?.let { ProfileValidator.birthdate(it, today).getOrThrow() }

            if (newUsername != null && newUsername != user.username &&
                isUsernameTaken(newUsername, userId)
            ) {
                throw ProfileConflictException(FieldError("username", "That username is already taken"))
            }
            if (newEmail != null && newEmail != user.email && isEmailTaken(newEmail, userId)) {
                throw ProfileConflictException(FieldError("email", "That email is already registered"))
            }

            newUsername?.let { user.username = it }
            newEmail?.let { user.email = it }
            if (request.phone != null) user.phone = newPhone
            if (request.fullName != null) user.fullName = newFullName
            if (request.gender != null) user.gender = newGender
            if (request.birthdate != null) user.birthdate = newBirthdate
            user.updatedAt = Clock.System.now()

            if (profileRow(userId) == null) createProfileRow(userId)
            if (request.bio != null || request.location != null || request.website != null) {
                ProfilesTable.update({ ProfilesTable.id eq userId }) { statement ->
                    if (request.bio != null) statement[bio] = newBio
                    if (request.location != null) statement[location] = newLocation
                    if (request.website != null) statement[website] = newWebsite
                    statement[updatedAt] = Clock.System.now()
                }
            }
            true
        }
        return if (applied) getProfile(userId, userId) else null
    }

    /** Stores a new avatar URL and returns the previous one so its file can be cleaned up. */
    suspend fun setAvatarUrl(userId: UUID, url: String?): String? = dbQuery {
        if (profileRow(userId) == null) createProfileRow(userId)
        val previous = profileRow(userId)?.get(ProfilesTable.avatarUrl)
        ProfilesTable.update({ ProfilesTable.id eq userId }) {
            it[avatarUrl] = url
            it[updatedAt] = Clock.System.now()
        }
        previous
    }

    /** Stores a new cover URL and returns the previous one so its file can be cleaned up. */
    suspend fun setCoverUrl(userId: UUID, url: String?): String? = dbQuery {
        if (profileRow(userId) == null) createProfileRow(userId)
        val previous = profileRow(userId)?.get(ProfilesTable.coverUrl)
        ProfilesTable.update({ ProfilesTable.id eq userId }) {
            it[coverUrl] = url
            it[updatedAt] = Clock.System.now()
        }
        previous
    }

    suspend fun follow(followerId: UUID, targetId: UUID): FollowStateResponse = dbQuery {
        if (followerId != targetId) {
            FollowsTable.insertIgnore {
                it[FollowsTable.followerId] = followerId
                it[followingId] = targetId
            }
            syncCounters(followerId, targetId)
            // Same transaction as the follow itself, so the two cannot disagree.
            NotificationWriter.recordFollow(actorId = followerId, recipientId = targetId)
        }
        FollowStateResponse(
            isFollowing = isFollowing(followerId, targetId),
            followerCount = countFollowers(targetId)
        )
    }

    suspend fun unfollow(followerId: UUID, targetId: UUID): FollowStateResponse = dbQuery {
        FollowsTable.deleteWhere {
            (FollowsTable.followerId eq followerId) and (FollowsTable.followingId eq targetId)
        }
        syncCounters(followerId, targetId)
        NotificationWriter.removeFollow(actorId = followerId, recipientId = targetId)
        FollowStateResponse(
            isFollowing = false,
            followerCount = countFollowers(targetId)
        )
    }

    /**
     * Finds people by username or full name.
     *
     * Matching is case-insensitive and substring-based, which is right for a few
     * thousand users. Swap in a trigram or full-text index before it gets larger.
     */
    suspend fun searchUsers(
        query: String,
        viewerId: UUID,
        cursor: String?,
        limit: Int
    ): UserSummaryPage = dbQuery {
        val term = query.trim()
        if (term.isBlank()) return@dbQuery UserSummaryPage(emptyList(), null, 0)

        val pattern = "%${term.lowercase().replace("%", "\\%").replace("_", "\\_")}%"
        var condition: Op<Boolean> =
            (UsersTable.username.lowerCase() like pattern) or
                (UsersTable.fullName.lowerCase() like pattern)
        cursor?.let { condition = condition and (UsersTable.username greater it) }

        val total = UsersTable.selectAll().where(condition).count().toInt()
        val rows = UsersTable
            .join(ProfilesTable, JoinType.LEFT, UsersTable.id, ProfilesTable.id)
            .selectAll()
            .where(condition)
            .orderBy(UsersTable.username to SortOrder.ASC)
            .limit(limit + 1)
            .toList()

        buildPage(rows, limit, viewerId, total)
    }

    /** People who follow [userId]. */
    suspend fun followers(userId: UUID, viewerId: UUID, cursor: String?, limit: Int): UserSummaryPage =
        peopleFrom(FollowsTable.followerId, FollowsTable.followingId, userId, viewerId, cursor, limit)

    /** People [userId] follows. */
    suspend fun following(userId: UUID, viewerId: UUID, cursor: String?, limit: Int): UserSummaryPage =
        peopleFrom(FollowsTable.followingId, FollowsTable.followerId, userId, viewerId, cursor, limit)

    /**
     * Shared body for the follower and following lists.
     *
     * @param selectColumn the side of `follows` holding the people to return.
     * @param matchColumn the side that must equal [userId].
     */
    private suspend fun peopleFrom(
        selectColumn: Column<EntityID<UUID>>,
        matchColumn: Column<EntityID<UUID>>,
        userId: UUID,
        viewerId: UUID,
        cursor: String?,
        limit: Int
    ): UserSummaryPage = dbQuery {
        var condition: Op<Boolean> = matchColumn eq userId
        cursor?.let { condition = condition and (UsersTable.username greater it) }

        val total = FollowsTable
            .join(UsersTable, JoinType.INNER, selectColumn, UsersTable.id)
            .selectAll().where(condition).count().toInt()

        val rows = FollowsTable
            .join(UsersTable, JoinType.INNER, selectColumn, UsersTable.id)
            .join(ProfilesTable, JoinType.LEFT, selectColumn, ProfilesTable.id)
            .selectAll()
            .where(condition)
            .orderBy(UsersTable.username to SortOrder.ASC)
            .limit(limit + 1)
            .toList()

        buildPage(rows, limit, viewerId, total)
    }

    /** Trims the lookahead row and resolves each person's follow state for the viewer. */
    private fun buildPage(
        rows: List<ResultRow>,
        limit: Int,
        viewerId: UUID,
        total: Int
    ): UserSummaryPage {
        val page = rows.take(limit)
        val items = page.map { row ->
            val id = row[UsersTable.id].value
            UserSummary(
                id = id.toString(),
                username = row[UsersTable.username],
                fullName = row[UsersTable.fullName],
                avatarUrl = row[ProfilesTable.avatarUrl],
                bio = row[ProfilesTable.bio],
                isMe = id == viewerId,
                isFollowing = id != viewerId && isFollowing(viewerId, id)
            )
        }
        return UserSummaryPage(
            items = items,
            nextCursor = if (rows.size > limit && page.isNotEmpty()) page.last()[UsersTable.username] else null,
            total = total
        )
    }

    // ---- internals -------------------------------------------------------

    private fun profileRow(userId: UUID): ResultRow? =
        ProfilesTable.selectAll().where { ProfilesTable.id eq userId }.singleOrNull()

    private fun createProfileRow(userId: UUID) {
        ProfilesTable.insertIgnore { it[id] = userId }
    }

    private fun isUsernameTaken(username: String, exceptUserId: UUID): Boolean =
        UsersTable.selectAll()
            .where { (UsersTable.username eq username) and (UsersTable.id neq exceptUserId) }
            .limit(1).any()

    private fun isEmailTaken(email: String, exceptUserId: UUID): Boolean =
        UsersTable.selectAll()
            .where { (UsersTable.email eq email) and (UsersTable.id neq exceptUserId) }
            .limit(1).any()

    private fun isFollowing(followerId: UUID, targetId: UUID): Boolean =
        FollowsTable.selectAll()
            .where { (FollowsTable.followerId eq followerId) and (FollowsTable.followingId eq targetId) }
            .limit(1).any()

    private fun countFollowers(userId: UUID): Int =
        FollowsTable.selectAll().where { FollowsTable.followingId eq userId }.count().toInt()

    private fun countFollowing(userId: UUID): Int =
        FollowsTable.selectAll().where { FollowsTable.followerId eq userId }.count().toInt()

    private fun countPosts(userId: UUID): Int =
        PostsTable.selectAll().where { PostsTable.authorId eq userId }.count().toInt()

    /** Keeps the denormalised counters on `profiles` in step with `follows`. */
    private fun syncCounters(vararg userIds: UUID) {
        userIds.distinct().forEach { userId ->
            if (profileRow(userId) == null) createProfileRow(userId)
            ProfilesTable.update({ ProfilesTable.id eq userId }) {
                it[followerCount] = countFollowers(userId)
                it[followingCount] = countFollowing(userId)
            }
        }
    }
}
