package com.linkup.repository

import com.linkup.database.DatingMatchesTable
import com.linkup.database.DatingMatchEntity
import com.linkup.database.DatingPhotoEntity
import com.linkup.database.DatingPhotosTable
import com.linkup.database.DatingProfilesTable
import com.linkup.database.DatingSwipesTable
import com.linkup.database.NotificationsTable
import com.linkup.database.ProfilesTable
import com.linkup.database.UserEntity
import com.linkup.database.UsersTable
import com.linkup.model.DatingCandidateResponse
import com.linkup.model.DatingNotificationResponse
import com.linkup.model.DatingPhotoDto
import com.linkup.model.DatingProfileRequest
import com.linkup.model.DatingProfileResponse
import com.linkup.model.MatchResponse
import com.linkup.model.SwipeRequest
import com.linkup.model.SwipeResponse
import com.linkup.storage.MediaStorage
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Period
import kotlinx.datetime.LocalDate
import java.util.UUID

class DatingRepository(private val mediaStorage: MediaStorage? = null) {
    fun getProfile(userId: UUID): DatingProfileResponse? = transaction {
        getProfileInTransaction(userId)?.withIdentity(userId)
    }

    /** The signed-in user's own name, age and avatar, so the profile screen never invents them. */
    fun identity(userId: UUID): DatingProfileResponse = transaction {
        (getProfileInTransaction(userId) ?: DatingProfileResponse(
            userId = userId.toString(),
            bio = null,
            interests = emptyList(),
            lookingFor = "RELATIONSHIP",
            preferredGender = null,
            minAge = null,
            maxAge = null
        )).withIdentity(userId)
    }

    private fun DatingProfileResponse.withIdentity(userId: UUID): DatingProfileResponse {
        val user = UserEntity[userId]
        val displayName = user.fullName ?: user.username
        val avatarUrl = ProfilesTable.selectAll()
            .where { ProfilesTable.id eq userId }
            .singleOrNull()?.get(ProfilesTable.avatarUrl)
        return copy(
            name = displayName,
            username = user.username,
            initials = displayName.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
            age = user.birthdate?.let {
                Period.between(java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth), java.time.LocalDate.now()).years
            },
            avatarUrl = avatarUrl
        )
    }

    private fun getProfileInTransaction(userId: UUID): DatingProfileResponse? =
        DatingProfilesTable
            .selectAll()
            .where { DatingProfilesTable.userId eq userId }
            .singleOrNull()
            ?.let { row ->
                DatingProfileResponse(
                    userId = userId.toString(),
                    bio = row[DatingProfilesTable.bio],
                    interests = row[DatingProfilesTable.interests]?.split(",")?.filter(String::isNotBlank) ?: emptyList(),
                    lookingFor = row[DatingProfilesTable.lookingFor],
                    preferredGender = row[DatingProfilesTable.preferredGender],
                    minAge = row[DatingProfilesTable.minAge],
                    maxAge = row[DatingProfilesTable.maxAge],
                    photos = photosFor(row[DatingProfilesTable.id].value)
                )
            }

    /** [profileId] is `dating_profiles.id`, which is what `dating_photos` points at — not the user id. */
    private fun photosFor(profileId: UUID): List<DatingPhotoDto> =
        DatingPhotosTable.selectAll()
            .where { DatingPhotosTable.datingProfileId eq profileId }
            .orderBy(DatingPhotosTable.displayOrder)
            .map { row ->
                DatingPhotoDto(
                    id = row[DatingPhotosTable.id].value.toString(),
                    photoUrl = row[DatingPhotosTable.photoUrl],
                    displayOrder = row[DatingPhotosTable.displayOrder]
                )
            }

    fun upsertProfile(userId: UUID, request: DatingProfileRequest): DatingProfileResponse = transaction {
        val existing = DatingProfilesTable.selectAll().where { DatingProfilesTable.userId eq userId }.singleOrNull()
        if (existing == null) DatingProfilesTable.insert {
            it[DatingProfilesTable.userId] = UserEntity[userId].id
            it[DatingProfilesTable.bio] = request.bio
            it[DatingProfilesTable.interests] = request.interests.joinToString(",")
            it[DatingProfilesTable.lookingFor] = request.lookingFor
            it[DatingProfilesTable.preferredGender] = request.preferredGender
            it[DatingProfilesTable.minAge] = request.minAge
            it[DatingProfilesTable.maxAge] = request.maxAge
        } else {
            DatingProfilesTable.update({ DatingProfilesTable.userId eq userId }) { row ->
                row[DatingProfilesTable.bio] = request.bio
                row[DatingProfilesTable.interests] = request.interests.joinToString(",")
                row[DatingProfilesTable.lookingFor] = request.lookingFor
                row[DatingProfilesTable.preferredGender] = request.preferredGender
                row[DatingProfilesTable.minAge] = request.minAge
                row[DatingProfilesTable.maxAge] = request.maxAge
            }
        }
        getProfileInTransaction(userId)!!
    }

    private fun profileIdFor(userId: UUID): UUID? =
        DatingProfilesTable.selectAll()
            .where { DatingProfilesTable.userId eq userId }
            .singleOrNull()?.get(DatingProfilesTable.id)?.value

    fun addPhoto(userId: UUID, photoUrl: String, displayOrder: Int): List<DatingPhotoDto> = transaction {
        val pid = profileIdFor(userId) ?: error("Create a dating profile before adding photos")
        DatingPhotosTable.insert {
            it[DatingPhotosTable.datingProfileId] = pid
            it[DatingPhotosTable.photoUrl] = photoUrl
            it[DatingPhotosTable.displayOrder] = displayOrder
        }
        photosFor(pid)
    }

    fun deletePhoto(userId: UUID, photoId: UUID): List<DatingPhotoDto> = transaction {
        val pid = profileIdFor(userId) ?: return@transaction emptyList()
        DatingPhotosTable.deleteWhere {
            (DatingPhotosTable.id eq photoId) and (DatingPhotosTable.datingProfileId eq pid)
        }
        photosFor(pid)
    }

    fun nextPhotoOrder(userId: UUID): Int = transaction {
        val pid = profileIdFor(userId) ?: return@transaction 0
        (photosFor(pid).maxOfOrNull { it.displayOrder } ?: -1) + 1
    }

    fun photoUrl(userId: UUID, photoId: UUID): String? = transaction {
        val pid = profileIdFor(userId) ?: return@transaction null
        DatingPhotosTable.selectAll()
            .where { (DatingPhotosTable.id eq photoId) and (DatingPhotosTable.datingProfileId eq pid) }
            .singleOrNull()?.get(DatingPhotosTable.photoUrl)
    }

    fun discover(userId: UUID): List<DatingCandidateResponse> = transaction {
        val profile = getProfileInTransaction(userId)
        val swiped = DatingSwipesTable.selectAll()
            .where { DatingSwipesTable.swiperId eq userId }
            .map { it[DatingSwipesTable.targetId].value }
            .toSet()
        val likedByUser = DatingSwipesTable.selectAll()
            .where { DatingSwipesTable.targetId eq userId }
            .associate { it[DatingSwipesTable.swiperId].value to it[DatingSwipesTable.direction] }
        val interests = profile?.interests?.toSet() ?: emptySet()
        val matchedUserIds = DatingMatchesTable.selectAll()
            .where {
                (DatingMatchesTable.user1Id eq userId) or (DatingMatchesTable.user2Id eq userId)
            }
            .flatMap { row ->
                listOf(row[DatingMatchesTable.user1Id].value, row[DatingMatchesTable.user2Id].value)
            }
            .filter { it != userId }
            .toSet()

        val candidateProfiles = DatingProfilesTable.selectAll()
            .associateBy { it[DatingProfilesTable.userId].value }

        UserEntity.all().toList().asSequence()
            .filter { it.id.value != userId && it.id.value !in swiped }
            .filter { it.id.value !in matchedUserIds }
            .filter { candidate ->
                val preferred = profile?.preferredGender
                preferred.isNullOrBlank() ||
                    preferred.equals("ANY", ignoreCase = true) ||
                    candidate.gender.isNullOrBlank() ||
                    candidate.gender.equals(preferred, ignoreCase = true)
            }
            .map { user ->
                val candidateProfile = candidateProfiles[user.id.value]
                val candidateInterests = candidateProfile?.get(DatingProfilesTable.interests)
                    ?.split(",")
                    ?.map(String::trim)
                    ?.filter(String::isNotBlank)
                    ?: emptyList()
                val age = user.birthdate?.let {
                    Period.between(java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth), java.time.LocalDate.now()).years
                }
                val commonInterests = interests.intersect(candidateInterests.toSet()).size
                val ageMatches = age != null && profile?.minAge != null && profile.maxAge != null && age in profile.minAge..profile.maxAge
                val likedYou = likedByUser[user.id.value] == "LIKE"
                val score = commonInterests * 30 +
                    (if (ageMatches) 20 else 0) +
                    (if (likedYou) 40 else 0) +
                    (if (!candidateProfile?.get(DatingProfilesTable.bio).isNullOrBlank()) 10 else 0)
                val photoUrl = candidateProfile?.get(DatingProfilesTable.id)?.value?.let { pid ->
                    DatingPhotosTable.selectAll()
                        .where { DatingPhotosTable.datingProfileId eq pid }
                        .orderBy(DatingPhotosTable.displayOrder)
                        .firstOrNull()?.get(DatingPhotosTable.photoUrl)
                }
                DatingCandidateResponse(
                    userId = user.id.value.toString(),
                    name = user.fullName ?: user.username,
                    username = user.username,
                    initials = (user.fullName ?: user.username).split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
                    age = age,
                    bio = candidateProfile?.get(DatingProfilesTable.bio),
                    interests = candidateInterests,
                    likedYou = likedYou,
                    compatibilityScore = score,
                    photoUrl = photoUrl
                )
            }
            .sortedWith(compareByDescending<DatingCandidateResponse> { it.likedYou }.thenByDescending { it.compatibilityScore })
            .toList()
    }

    /** Removes the user's PASS history so previously passed profiles can be rediscovered. */
    fun resetPassedSwipes(userId: UUID) = transaction {
        DatingSwipesTable.deleteWhere {
            (DatingSwipesTable.swiperId eq userId) and (DatingSwipesTable.direction eq "PASS")
        }
    }

    fun swipe(userId: UUID, request: SwipeRequest): SwipeResponse = transaction {
        val targetId = UUID.fromString(request.targetUserId)
        require(targetId != userId) { "Cannot swipe yourself" }
        require(request.decision == "LIKE" || request.decision == "PASS") { "Invalid decision" }
        val swiper = UserEntity[userId]
        val target = UserEntity[targetId]
        val existingSwipe = DatingSwipesTable.selectAll().where {
            (DatingSwipesTable.swiperId eq userId) and (DatingSwipesTable.targetId eq targetId)
        }.singleOrNull()
        if (existingSwipe == null) {
            DatingSwipesTable.insert {
                it[DatingSwipesTable.swiperId] = swiper.id
                it[DatingSwipesTable.targetId] = target.id
                it[DatingSwipesTable.direction] = request.decision
            }
        } else {
            DatingSwipesTable.update({
                (DatingSwipesTable.swiperId eq userId) and (DatingSwipesTable.targetId eq targetId)
            }) {
                it[DatingSwipesTable.direction] = request.decision
            }
        }
        if (request.decision != "LIKE") return@transaction SwipeResponse(request.decision, false)
        val reverseLike = DatingSwipesTable.selectAll().where {
            (DatingSwipesTable.swiperId eq targetId) and
                (DatingSwipesTable.targetId eq userId) and
                (DatingSwipesTable.direction eq "LIKE")
        }.any()
        if (!reverseLike) return@transaction SwipeResponse(request.decision, false)
        val existing = DatingMatchesTable.selectAll().where {
            ((DatingMatchesTable.user1Id eq userId) and (DatingMatchesTable.user2Id eq targetId)) or
                ((DatingMatchesTable.user1Id eq targetId) and (DatingMatchesTable.user2Id eq userId))
        }.singleOrNull()
        val matchId = existing?.get(DatingMatchesTable.id) ?: DatingMatchesTable.insertAndGetId {
            it[DatingMatchesTable.user1Id] = swiper.id
            it[DatingMatchesTable.user2Id] = target.id
        }
        if (existing == null) {
            NotificationsTable.insert {
                it[NotificationsTable.recipientId] = swiper.id
                it[NotificationsTable.actorId] = target.id
                it[NotificationsTable.type] = "DATING_MATCH"
                it[NotificationsTable.targetId] = matchId.value
            }
            NotificationsTable.insert {
                it[NotificationsTable.recipientId] = target.id
                it[NotificationsTable.actorId] = swiper.id
                it[NotificationsTable.type] = "DATING_MATCH"
                it[NotificationsTable.targetId] = matchId.value
            }
        }
        SwipeResponse(request.decision, true, MatchResponse(matchId.value.toString(), targetId.toString(), target.fullName ?: target.username, target.username, (target.fullName ?: target.username).take(2).uppercase(), "Now"))
    }

    fun notifications(userId: UUID): List<DatingNotificationResponse> = transaction {
        NotificationsTable.selectAll()
            .where { NotificationsTable.recipientId eq userId }
            .orderBy(NotificationsTable.createdAt, org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { row ->
                val actor = UserEntity[row[NotificationsTable.actorId].value]
                DatingNotificationResponse(
                    id = row[NotificationsTable.id].value.toString(),
                    actorName = actor.fullName ?: actor.username,
                    type = row[NotificationsTable.type],
                    targetId = row[NotificationsTable.targetId]?.toString(),
                    isRead = row[NotificationsTable.isRead],
                    createdAt = row[NotificationsTable.createdAt].toString()
                )
            }
    }

    fun matches(userId: UUID): List<MatchResponse> = transaction {
        DatingMatchesTable.selectAll().where {
            (DatingMatchesTable.user1Id eq userId) or (DatingMatchesTable.user2Id eq userId)
        }.map { row ->
            val matchedUserId = if (row[DatingMatchesTable.user1Id].value == userId) row[DatingMatchesTable.user2Id].value else row[DatingMatchesTable.user1Id].value
            val matchedUser = UserEntity[matchedUserId]
            MatchResponse(row[DatingMatchesTable.id].value.toString(), matchedUserId.toString(), matchedUser.fullName ?: matchedUser.username, matchedUser.username, (matchedUser.fullName ?: matchedUser.username).take(2).uppercase(), row[DatingMatchesTable.createdAt].toString())
        }
    }
}
