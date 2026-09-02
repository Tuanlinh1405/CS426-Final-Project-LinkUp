package com.linkup.repository

import com.linkup.database.FriendshipsTable
import com.linkup.service.FriendshipRow
import com.linkup.service.FriendshipRules
import com.linkup.service.FriendshipStatus
import com.linkup.service.StoredFriendship
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

/**
 * Transaction-scoped reads of the friendship graph.
 *
 * Plain functions, like [NotificationWriter], so both `ProfileRepository` and
 * `FriendRepository` can ask the same questions inside their own transactions
 * without depending on each other.
 *
 * Friend ids are resolved in memory rather than with a recursive query. That is the
 * right trade at this scale — a few hundred friends each — and keeps mutual-friend
 * and suggestion logic readable. Move to SQL aggregation if lists ever get large.
 */
object FriendGraph {

    /** The single row between two people, in whichever direction it was created. */
    fun rowBetween(a: UUID, b: UUID): StoredFriendship? =
        FriendshipsTable.selectAll()
            .where {
                ((FriendshipsTable.requesterId eq a) and (FriendshipsTable.addresseeId eq b)) or
                    ((FriendshipsTable.requesterId eq b) and (FriendshipsTable.addresseeId eq a))
            }
            .limit(1)
            .firstOrNull()
            ?.let {
                StoredFriendship(
                    requesterId = it[FriendshipsTable.requesterId].value,
                    addresseeId = it[FriendshipsTable.addresseeId].value,
                    status = it[FriendshipsTable.status]
                )
            }

    fun statusBetween(viewerId: UUID, otherId: UUID): FriendshipStatus {
        if (viewerId == otherId) return FriendshipStatus.NONE
        return FriendshipRules.statusFor(rowBetween(viewerId, otherId), viewerId)
    }

    /** Everyone [userId] is actually friends with, in either direction. */
    fun friendIds(userId: UUID): Set<UUID> =
        FriendshipsTable.selectAll()
            .where {
                (FriendshipsTable.status eq FriendshipRow.ACCEPTED) and
                    ((FriendshipsTable.requesterId eq userId) or
                        (FriendshipsTable.addresseeId eq userId))
            }
            .mapTo(mutableSetOf()) { row ->
                val requester = row[FriendshipsTable.requesterId].value
                if (requester == userId) row[FriendshipsTable.addresseeId].value else requester
            }

    fun friendCount(userId: UUID): Int =
        FriendshipsTable.selectAll()
            .where {
                (FriendshipsTable.status eq FriendshipRow.ACCEPTED) and
                    ((FriendshipsTable.requesterId eq userId) or
                        (FriendshipsTable.addresseeId eq userId))
            }
            .count()
            .toInt()

    /** How many friends two people have in common — the "3 mutual friends" line. */
    fun mutualFriendCount(a: UUID, b: UUID): Int {
        if (a == b) return 0
        val theirs = friendIds(b)
        if (theirs.isEmpty()) return 0
        return friendIds(a).count { it in theirs }
    }

    /** Ids with a pending request in either direction, so they can be excluded. */
    fun pendingIds(userId: UUID): Set<UUID> =
        FriendshipsTable.selectAll()
            .where {
                (FriendshipsTable.status eq FriendshipRow.PENDING) and
                    ((FriendshipsTable.requesterId eq userId) or
                        (FriendshipsTable.addresseeId eq userId))
            }
            .mapTo(mutableSetOf()) { row ->
                val requester = row[FriendshipsTable.requesterId].value
                if (requester == userId) row[FriendshipsTable.addresseeId].value else requester
            }
}
