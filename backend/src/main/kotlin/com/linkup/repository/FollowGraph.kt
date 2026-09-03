package com.linkup.repository

import com.linkup.database.FollowsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

/**
 * Transaction-scoped reads of the follow graph.
 *
 * Follows are one-directional and need no acceptance, which is what separates them
 * from [FriendGraph]: following is subscribing, friendship is mutual and negotiated.
 * Both live behind plain objects so any repository can ask inside its own transaction.
 */
object FollowGraph {

    fun isFollowing(followerId: UUID, targetId: UUID): Boolean {
        if (followerId == targetId) return false
        return FollowsTable.selectAll()
            .where {
                (FollowsTable.followerId eq followerId) and (FollowsTable.followingId eq targetId)
            }
            .limit(1)
            .any()
    }

    fun followerCount(userId: UUID): Int =
        FollowsTable.selectAll().where { FollowsTable.followingId eq userId }.count().toInt()

    fun followingCount(userId: UUID): Int =
        FollowsTable.selectAll().where { FollowsTable.followerId eq userId }.count().toInt()
}
