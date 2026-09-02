package com.linkup.repository

import com.linkup.database.NotificationsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import java.util.UUID

/** The notification kinds the app can produce. Stored as the string name. */
enum class NotificationType {
    FOLLOW,
    LIKE,
    COMMENT,
    MENTION,
    MESSAGE,
    DATING_MATCH,
    SYSTEM
}

/**
 * Transaction-scoped notification writes.
 *
 * These are plain functions rather than suspend ones so a feature can record a
 * notification inside its own transaction — following someone and being notified
 * about it either both happen or neither does.
 */
object NotificationWriter {

    /**
     * Records that [actorId] followed [recipientId].
     *
     * Any earlier FOLLOW notification from the same actor is removed first, so
     * toggling follow on and off cannot pile up duplicate rows; the notification
     * simply moves back to the top of the list.
     */
    fun recordFollow(actorId: UUID, recipientId: UUID) {
        if (actorId == recipientId) return
        removeFollow(actorId, recipientId)
        NotificationsTable.insert {
            it[NotificationsTable.recipientId] = recipientId
            it[NotificationsTable.actorId] = actorId
            it[type] = NotificationType.FOLLOW.name
            it[targetId] = actorId
            it[isRead] = false
        }
    }

    /** Drops the FOLLOW notification when the follow is undone. */
    fun removeFollow(actorId: UUID, recipientId: UUID) {
        NotificationsTable.deleteWhere {
            (NotificationsTable.recipientId eq recipientId) and
                (NotificationsTable.actorId eq actorId) and
                (NotificationsTable.type eq NotificationType.FOLLOW.name)
        }
    }

    /**
     * Greets a newly registered user.
     *
     * The actor is the user themselves: `notifications.actor_id` is NOT NULL, and a
     * self-actor keeps the foreign key honest without inventing a system account.
     */
    fun recordWelcome(userId: UUID) {
        NotificationsTable.insert {
            it[recipientId] = userId
            it[actorId] = userId
            it[type] = NotificationType.SYSTEM.name
            it[targetId] = null
            it[isRead] = false
        }
    }
}
