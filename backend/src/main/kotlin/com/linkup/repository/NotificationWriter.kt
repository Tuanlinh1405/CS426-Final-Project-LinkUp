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
    FRIEND_REQUEST,
    FRIEND_ACCEPT,
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
     * Tells [recipientId] that [actorId] wants to be friends.
     *
     * Replaces any earlier request notification from the same person, so a
     * cancel-and-resend cannot stack up rows.
     */
    fun recordFriendRequest(actorId: UUID, recipientId: UUID) {
        if (actorId == recipientId) return
        remove(actorId, recipientId, NotificationType.FRIEND_REQUEST)
        insert(actorId, recipientId, NotificationType.FRIEND_REQUEST, targetId = actorId)
    }

    /**
     * Tells the original requester that [actorId] accepted.
     *
     * The request notification in the accepter's own inbox is dropped at the call
     * site: it has been acted on, and leaving it there invites a second response.
     */
    fun recordFriendAccept(actorId: UUID, recipientId: UUID) {
        if (actorId == recipientId) return
        remove(actorId, recipientId, NotificationType.FRIEND_ACCEPT)
        insert(actorId, recipientId, NotificationType.FRIEND_ACCEPT, targetId = actorId)
    }

    /** Withdraws a request notification when the request itself goes away. */
    fun removeFriendRequest(actorId: UUID, recipientId: UUID) {
        remove(actorId, recipientId, NotificationType.FRIEND_REQUEST)
    }

    /** Clears every friend notification between two people, in both directions. */
    fun removeFriendNotifications(a: UUID, b: UUID) {
        listOf(NotificationType.FRIEND_REQUEST, NotificationType.FRIEND_ACCEPT).forEach { type ->
            remove(a, b, type)
            remove(b, a, type)
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

    private fun insert(actor: UUID, recipient: UUID, type: NotificationType, targetId: UUID?) {
        NotificationsTable.insert {
            it[recipientId] = recipient
            it[actorId] = actor
            it[NotificationsTable.type] = type.name
            it[NotificationsTable.targetId] = targetId
            it[isRead] = false
        }
    }

    private fun remove(actor: UUID, recipient: UUID, type: NotificationType) {
        NotificationsTable.deleteWhere {
            (NotificationsTable.recipientId eq recipient) and
                (NotificationsTable.actorId eq actor) and
                (NotificationsTable.type eq type.name)
        }
    }
}
