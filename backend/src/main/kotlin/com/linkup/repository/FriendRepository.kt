package com.linkup.repository

import com.linkup.database.DatabaseFactory.dbQuery
import com.linkup.database.FriendshipsTable
import com.linkup.database.ProfilesTable
import com.linkup.database.UsersTable
import com.linkup.model.FriendshipStateResponse
import com.linkup.model.UserSummary
import com.linkup.model.UserSummaryPage
import com.linkup.service.FriendshipRow
import com.linkup.service.FriendshipRules
import com.linkup.service.FriendshipStatus
import com.linkup.service.RequestOutcome
import com.linkup.service.ResponseOutcome
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

/** Raised when a friend action is not allowed from the caller's current state. */
class FriendActionException(override val message: String) : RuntimeException(message)

class FriendRepository {

    /** Current relationship between the caller and someone else. */
    suspend fun state(viewerId: UUID, otherId: UUID): FriendshipStateResponse = dbQuery {
        stateOf(viewerId, otherId)
    }

    /**
     * Sends a friend request.
     *
     * Asking someone who already asked you accepts their request instead of creating
     * a second row, and asking twice is a no-op rather than an error — both decided
     * by [FriendshipRules], which is unit tested on its own.
     */
    suspend fun sendRequest(requesterId: UUID, addresseeId: UUID): FriendshipStateResponse = dbQuery {
        val existing = FriendGraph.rowBetween(requesterId, addresseeId)

        when (val outcome = FriendshipRules.requestOutcome(existing, requesterId, addresseeId)) {
            is RequestOutcome.Rejected -> throw FriendActionException(outcome.reason)

            RequestOutcome.AlreadyFriends, RequestOutcome.AlreadyPending -> Unit

            RequestOutcome.Create -> {
                FriendshipsTable.insert {
                    it[FriendshipsTable.requesterId] = requesterId
                    it[FriendshipsTable.addresseeId] = addresseeId
                    it[status] = FriendshipRow.PENDING
                }
                NotificationWriter.recordFriendRequest(actorId = requesterId, recipientId = addresseeId)
            }

            RequestOutcome.AcceptExisting -> {
                acceptRow(requesterId = addresseeId, addresseeId = requesterId)
                // The person who asked first is the one who learns they are now friends.
                NotificationWriter.removeFriendRequest(actorId = addresseeId, recipientId = requesterId)
                NotificationWriter.recordFriendAccept(actorId = requesterId, recipientId = addresseeId)
            }
        }
        stateOf(requesterId, addresseeId)
    }

    /** Withdraws a request the caller sent. */
    suspend fun cancelRequest(cancellerId: UUID, otherId: UUID): FriendshipStateResponse = dbQuery {
        val existing = FriendGraph.rowBetween(cancellerId, otherId)
        when (val outcome = FriendshipRules.cancelOutcome(existing, cancellerId)) {
            is ResponseOutcome.Rejected -> throw FriendActionException(outcome.reason)
            ResponseOutcome.Apply -> {
                deleteRow(cancellerId, otherId)
                NotificationWriter.removeFriendRequest(actorId = cancellerId, recipientId = otherId)
            }
        }
        stateOf(cancellerId, otherId)
    }

    /**
     * Accepts or declines a request addressed to the caller.
     *
     * Either way the request notification is cleared from the responder's inbox: it
     * has been dealt with, and leaving it invites a second, confusing response.
     */
    suspend fun respond(
        responderId: UUID,
        otherId: UUID,
        accept: Boolean
    ): FriendshipStateResponse = dbQuery {
        val existing = FriendGraph.rowBetween(responderId, otherId)
        when (val outcome = FriendshipRules.responseOutcome(existing, responderId)) {
            is ResponseOutcome.Rejected -> throw FriendActionException(outcome.reason)
            ResponseOutcome.Apply -> {
                NotificationWriter.removeFriendRequest(actorId = otherId, recipientId = responderId)
                if (accept) {
                    acceptRow(requesterId = otherId, addresseeId = responderId)
                    NotificationWriter.recordFriendAccept(actorId = responderId, recipientId = otherId)
                } else {
                    // Declining removes the row entirely, so they may ask again later.
                    deleteRow(responderId, otherId)
                }
            }
        }
        stateOf(responderId, otherId)
    }

    suspend fun unfriend(userId: UUID, otherId: UUID): FriendshipStateResponse = dbQuery {
        val existing = FriendGraph.rowBetween(userId, otherId)
        when (val outcome = FriendshipRules.unfriendOutcome(existing)) {
            is ResponseOutcome.Rejected -> throw FriendActionException(outcome.reason)
            ResponseOutcome.Apply -> {
                deleteRow(userId, otherId)
                NotificationWriter.removeFriendNotifications(userId, otherId)
            }
        }
        stateOf(userId, otherId)
    }

    /** [userId]'s friends, as seen by [viewerId]. */
    suspend fun friends(
        userId: UUID,
        viewerId: UUID,
        cursor: String?,
        limit: Int
    ): UserSummaryPage = dbQuery {
        pageOfUsers(FriendGraph.friendIds(userId), viewerId, cursor, limit)
    }

    /** Requests waiting on the caller to answer. */
    suspend fun incomingRequests(userId: UUID, cursor: String?, limit: Int): UserSummaryPage = dbQuery {
        pageOfUsers(requestIds(userId, incoming = true), userId, cursor, limit)
    }

    /** Requests the caller has sent and not yet had answered. */
    suspend fun outgoingRequests(userId: UUID, cursor: String?, limit: Int): UserSummaryPage = dbQuery {
        pageOfUsers(requestIds(userId, incoming = false), userId, cursor, limit)
    }

    suspend fun incomingRequestCount(userId: UUID): Int = dbQuery {
        FriendshipsTable.selectAll()
            .where {
                (FriendshipsTable.status eq FriendshipRow.PENDING) and
                    (FriendshipsTable.addresseeId eq userId)
            }
            .count()
            .toInt()
    }

    /**
     * "People you may know": friends of your friends, ranked by how many you share.
     *
     * A brand new account has no friends and so no friends-of-friends; rather than
     * show an empty shelf, it falls back to the newest accounts. Both paths exclude
     * the caller, existing friends and anyone already involved in a pending request.
     */
    suspend fun suggestions(userId: UUID, limit: Int): UserSummaryPage = dbQuery {
        val myFriends = FriendGraph.friendIds(userId)
        val pending = FriendGraph.pendingIds(userId)
        val excluded = myFriends + pending + userId

        val ranked = LinkedHashMap<UUID, Int>()
        myFriends.forEach { friend ->
            FriendGraph.friendIds(friend).forEach { candidate ->
                if (candidate !in excluded) ranked.merge(candidate, 1, Int::plus)
            }
        }

        val ids = if (ranked.isNotEmpty()) {
            ranked.entries.sortedByDescending { it.value }.take(limit).map { it.key }
        } else {
            UsersTable.selectAll()
                .orderBy(UsersTable.createdAt to SortOrder.DESC)
                .limit(limit + excluded.size)
                .map { it[UsersTable.id].value }
                .filterNot { it in excluded }
                .take(limit)
        }

        val summaries = summariesFor(ids, userId)
        // Preserve the ranking: summariesFor returns database order.
        val byId = summaries.associateBy { it.id }
        UserSummaryPage(
            items = ids.mapNotNull { byId[it.toString()] },
            nextCursor = null,
            total = summaries.size
        )
    }

    // ---- internals -------------------------------------------------------

    private fun stateOf(viewerId: UUID, otherId: UUID): FriendshipStateResponse =
        FriendshipStateResponse(
            status = FriendGraph.statusBetween(viewerId, otherId).name,
            friendCount = FriendGraph.friendCount(viewerId),
            mutualFriendCount = FriendGraph.mutualFriendCount(viewerId, otherId),
            incomingRequestCount = FriendshipsTable.selectAll()
                .where {
                    (FriendshipsTable.status eq FriendshipRow.PENDING) and
                        (FriendshipsTable.addresseeId eq viewerId)
                }
                .count()
                .toInt()
        )

    private fun acceptRow(requesterId: UUID, addresseeId: UUID) {
        FriendshipsTable.update({
            (FriendshipsTable.requesterId eq requesterId) and
                (FriendshipsTable.addresseeId eq addresseeId)
        }) {
            it[status] = FriendshipRow.ACCEPTED
            it[respondedAt] = Clock.System.now()
        }
    }

    private fun deleteRow(a: UUID, b: UUID) {
        FriendshipsTable.deleteWhere {
            ((FriendshipsTable.requesterId eq a) and (FriendshipsTable.addresseeId eq b)) or
                ((FriendshipsTable.requesterId eq b) and (FriendshipsTable.addresseeId eq a))
        }
    }

    private fun requestIds(userId: UUID, incoming: Boolean): Set<UUID> =
        FriendshipsTable.selectAll()
            .where {
                if (incoming) {
                    (FriendshipsTable.status eq FriendshipRow.PENDING) and
                        (FriendshipsTable.addresseeId eq userId)
                } else {
                    (FriendshipsTable.status eq FriendshipRow.PENDING) and
                        (FriendshipsTable.requesterId eq userId)
                }
            }
            .mapTo(mutableSetOf()) {
                if (incoming) {
                    it[FriendshipsTable.requesterId].value
                } else {
                    it[FriendshipsTable.addresseeId].value
                }
            }

    /** Pages a known set of user ids alphabetically, with a username cursor. */
    private fun pageOfUsers(
        ids: Set<UUID>,
        viewerId: UUID,
        cursor: String?,
        limit: Int
    ): UserSummaryPage {
        if (ids.isEmpty()) return UserSummaryPage(emptyList(), null, 0)

        var condition: Op<Boolean> = UsersTable.id inList ids.toList()
        cursor?.let { condition = condition and (UsersTable.username greater it) }

        val rows = UsersTable
            .join(ProfilesTable, JoinType.LEFT, UsersTable.id, ProfilesTable.id)
            .selectAll()
            .where(condition)
            .orderBy(UsersTable.username to SortOrder.ASC)
            .limit(limit + 1)
            .toList()

        val page = rows.take(limit)
        return UserSummaryPage(
            items = page.map { it.toSummary(viewerId) },
            nextCursor = if (rows.size > limit && page.isNotEmpty()) {
                page.last()[UsersTable.username]
            } else {
                null
            },
            total = ids.size
        )
    }

    private fun summariesFor(ids: List<UUID>, viewerId: UUID): List<UserSummary> {
        if (ids.isEmpty()) return emptyList()
        return UsersTable
            .join(ProfilesTable, JoinType.LEFT, UsersTable.id, ProfilesTable.id)
            .selectAll()
            .where { UsersTable.id inList ids }
            .map { it.toSummary(viewerId) }
    }

    private fun ResultRow.toSummary(viewerId: UUID): UserSummary {
        val id = this[UsersTable.id].value
        return UserSummary(
            id = id.toString(),
            username = this[UsersTable.username],
            fullName = this[UsersTable.fullName],
            avatarUrl = this[ProfilesTable.avatarUrl],
            bio = this[ProfilesTable.bio],
            isMe = id == viewerId,
            isFollowing = id != viewerId && FollowGraph.isFollowing(viewerId, id),
            friendshipStatus = if (id == viewerId) {
                FriendshipStatus.NONE.name
            } else {
                FriendGraph.statusBetween(viewerId, id).name
            },
            mutualFriendCount = if (id == viewerId) 0 else FriendGraph.mutualFriendCount(viewerId, id)
        )
    }
}
