package com.linkup.repository

import com.linkup.database.DatabaseFactory.dbQuery
import com.linkup.database.NotificationsTable
import com.linkup.database.ProfilesTable
import com.linkup.database.UsersTable
import com.linkup.model.NotificationActorDto
import com.linkup.model.NotificationDto
import com.linkup.model.NotificationPageDto
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

class NotificationRepository {

    companion object {
        const val DEFAULT_LIMIT = 25
        const val MAX_LIMIT = 50
    }

    /**
     * One page of the recipient's notifications, newest first.
     *
     * Paging is by cursor rather than offset: rows arrive constantly at the top of
     * this list, and an offset would silently skip or repeat items as they shift.
     */
    suspend fun list(
        recipientId: UUID,
        cursor: String?,
        limit: Int,
        unreadOnly: Boolean
    ): NotificationPageDto = dbQuery {
        val pageSize = limit.coerceIn(1, MAX_LIMIT)

        var condition: Op<Boolean> = NotificationsTable.recipientId eq recipientId
        if (unreadOnly) {
            condition = condition and (NotificationsTable.isRead eq false)
        }
        decodeCursor(cursor)?.let { (createdAt, id) ->
            condition = condition and (
                (NotificationsTable.createdAt less createdAt) or
                    ((NotificationsTable.createdAt eq createdAt) and (NotificationsTable.id less id))
                )
        }

        // One extra row tells us whether another page exists without a second count.
        val rows = NotificationsTable
            .join(UsersTable, JoinType.INNER, NotificationsTable.actorId, UsersTable.id)
            .join(ProfilesTable, JoinType.LEFT, NotificationsTable.actorId, ProfilesTable.id)
            .selectAll()
            .where(condition)
            .orderBy(
                NotificationsTable.createdAt to SortOrder.DESC,
                NotificationsTable.id to SortOrder.DESC
            )
            .limit(pageSize + 1)
            .toList()

        val page = rows.take(pageSize)
        val nextCursor = if (rows.size > pageSize && page.isNotEmpty()) {
            encodeCursor(page.last())
        } else {
            null
        }

        NotificationPageDto(
            items = page.map { it.toNotificationDto() },
            nextCursor = nextCursor,
            unreadCount = countUnread(recipientId)
        )
    }

    suspend fun unreadCount(recipientId: UUID): Int = dbQuery { countUnread(recipientId) }

    /** @return true when a notification owned by [recipientId] was updated. */
    suspend fun markRead(recipientId: UUID, id: UUID, read: Boolean): Boolean = dbQuery {
        NotificationsTable.update({
            (NotificationsTable.id eq id) and (NotificationsTable.recipientId eq recipientId)
        }) {
            it[isRead] = read
        } > 0
    }

    /** @return how many rows changed. */
    suspend fun markAllRead(recipientId: UUID): Int = dbQuery {
        NotificationsTable.update({
            (NotificationsTable.recipientId eq recipientId) and (NotificationsTable.isRead eq false)
        }) {
            it[isRead] = true
        }
    }

    suspend fun delete(recipientId: UUID, id: UUID): Boolean = dbQuery {
        NotificationsTable.deleteWhere {
            (NotificationsTable.id eq id) and (NotificationsTable.recipientId eq recipientId)
        } > 0
    }

    suspend fun clearAll(recipientId: UUID): Int = dbQuery {
        NotificationsTable.deleteWhere { NotificationsTable.recipientId eq recipientId }
    }

    // ---- internals -------------------------------------------------------

    private fun countUnread(recipientId: UUID): Int =
        NotificationsTable.selectAll()
            .where {
                (NotificationsTable.recipientId eq recipientId) and
                    (NotificationsTable.isRead eq false)
            }
            .count()
            .toInt()

    private fun ResultRow.toNotificationDto(): NotificationDto = NotificationDto(
        id = this[NotificationsTable.id].value.toString(),
        type = this[NotificationsTable.type],
        actor = NotificationActorDto(
            id = this[NotificationsTable.actorId].value.toString(),
            username = this[UsersTable.username],
            fullName = this[UsersTable.fullName],
            avatarUrl = this[ProfilesTable.avatarUrl]
        ),
        targetId = this[NotificationsTable.targetId]?.toString(),
        isRead = this[NotificationsTable.isRead],
        createdAt = this[NotificationsTable.createdAt].toString()
    )

    /**
     * The timestamp alone is not a safe cursor: two notifications can share one, and
     * the second would be skipped. Pairing it with the id makes the sort total.
     */
    private fun encodeCursor(row: ResultRow): String =
        "${row[NotificationsTable.createdAt]}_${row[NotificationsTable.id].value}"

    private fun decodeCursor(cursor: String?): Pair<Instant, UUID>? {
        if (cursor.isNullOrBlank()) return null
        val separator = cursor.lastIndexOf('_')
        if (separator <= 0) return null
        return try {
            Instant.parse(cursor.substring(0, separator)) to
                UUID.fromString(cursor.substring(separator + 1))
        } catch (e: IllegalArgumentException) {
            // A malformed cursor should start from the top, not blow up the request.
            null
        }
    }
}
