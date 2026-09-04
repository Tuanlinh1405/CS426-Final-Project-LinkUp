package com.linkup.repository

import com.linkup.database.ConversationMembersTable
import com.linkup.database.ConversationsTable
import com.linkup.database.DatabaseFactory.dbQuery
import com.linkup.database.DatabaseFactory.rawRead
import com.linkup.database.MessageReceiptsTable
import com.linkup.database.MessagesTable
import com.linkup.database.ProfilesTable
import com.linkup.database.UsersTable
import com.linkup.model.ConversationResponse
import com.linkup.model.MessageResponse
import com.linkup.model.ParticipantDto
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import java.sql.ResultSet
import java.util.UUID

class ChatRepository {

    /**
     * Loads the whole conversation list in a single round trip. Each hop to the Supabase
     * pooler costs ~170ms, so the previous seven-query version spent ~1.2s purely waiting
     * on the network. Uses rawRead (no transaction wrapper) to avoid the extra
     * BEGIN/COMMIT round trips Exposed would add.
     */
    suspend fun getConversationsForUser(userId: UUID): List<ConversationResponse> {
        val uid = userId.toString()
        return rawRead { conn ->
            conn.prepareStatement(CONVERSATION_LIST_SQL).use { ps ->
                repeat(5) { ps.setObject(it + 1, userId) }
                ps.executeQuery().use { rs -> readConversationRows(rs) }
            }
        }.groupBy { it.conversationId }
            .map { (convId, group) ->
                val head = group.first()
                ConversationResponse(
                    id = convId,
                    type = head.type,
                    name = head.name,
                    participants = group.map { it.participant },
                    lastMessage = head.lastMessage,
                    unreadCount = head.unreadCount,
                    updatedAt = head.updatedAt
                )
            }
    }

    private class ConversationRow(
        val conversationId: String,
        val type: String,
        val name: String?,
        val updatedAt: String,
        val participant: ParticipantDto,
        val lastMessage: MessageResponse?,
        val unreadCount: Int
    )

    private fun readConversationRows(rs: ResultSet): List<ConversationRow> {
        val rows = mutableListOf<ConversationRow>()
        while (rs.next()) {
            val conversationId = rs.getString("conv_id")
            val lastMessageId = rs.getString("lm_id")
            rows += ConversationRow(
                conversationId = conversationId,
                type = rs.getString("conv_type"),
                name = rs.getString("conv_name"),
                updatedAt = rs.isoTimestamp("conv_updated_at"),
                participant = ParticipantDto(
                    id = rs.getString("p_id"),
                    username = rs.getString("p_username"),
                    fullName = rs.getString("p_full_name"),
                    avatarUrl = rs.getString("p_avatar")
                ),
                lastMessage = if (lastMessageId == null) null else MessageResponse(
                    id = lastMessageId,
                    conversationId = conversationId,
                    senderId = rs.getString("lm_sender_id"),
                    senderName = rs.getString("lm_sender_name"),
                    type = rs.getString("lm_type"),
                    textContent = rs.getString("lm_text"),
                    mediaUrl = rs.getString("lm_media_url"),
                    sharedContentId = rs.getString("lm_shared_content_id"),
                    status = rs.getString("lm_status") ?: "SENT",
                    createdAt = rs.isoTimestamp("lm_created_at")
                ),
                unreadCount = rs.getInt("unread_count")
            )
        }
        return rows
    }

    /** Clients parse timestamps as ISO-8601, which is what Exposed's Instant mapping produced. */
    private fun ResultSet.isoTimestamp(column: String): String =
        getTimestamp(column)?.toInstant()?.toString() ?: ""

    suspend fun getOrCreateDirectConversation(user1Id: UUID, user2Id: UUID): ConversationResponse = dbQuery {
        val user1Convs = ConversationMembersTable
            .select(ConversationMembersTable.conversationId)
            .where { ConversationMembersTable.userId eq user1Id }
            .map { it[ConversationMembersTable.conversationId].value }

        val existingConvId = if (user1Convs.isNotEmpty()) {
            ConversationMembersTable
                .select(ConversationMembersTable.conversationId)
                .where { 
                    (ConversationMembersTable.userId eq user2Id) and 
                    (ConversationMembersTable.conversationId inList user1Convs) 
                }
                .map { it[ConversationMembersTable.conversationId].value }
                .firstOrNull { convId ->
                    ConversationsTable
                        .selectAll()
                        .where { (ConversationsTable.id eq convId) and (ConversationsTable.type eq "DIRECT") }
                        .count() > 0
                }
        } else null

        if (existingConvId != null) {
            val convRow = ConversationsTable.selectAll().where { ConversationsTable.id eq existingConvId }.single()
            return@dbQuery ConversationResponse(
                id = existingConvId.toString(),
                type = "DIRECT",
                name = null,
                participants = getParticipantsForConversationInternal(existingConvId),
                lastMessage = getLastMessageForConversationInternal(existingConvId, user1Id),
                unreadCount = getUnreadCountInternal(existingConvId, user1Id),
                updatedAt = convRow[ConversationsTable.updatedAt].toString()
            )
        }

        val newConvId = ConversationsTable.insertAndGetId {
            it[type] = "DIRECT"
            it[createdAt] = Clock.System.now()
            it[updatedAt] = Clock.System.now()
        }.value

        ConversationMembersTable.insert {
            it[conversationId] = newConvId
            it[userId] = user1Id
            it[joinedAt] = Clock.System.now()
            it[lastReadAt] = Clock.System.now()
        }
        ConversationMembersTable.insert {
            it[conversationId] = newConvId
            it[userId] = user2Id
            it[joinedAt] = Clock.System.now()
            it[lastReadAt] = Clock.System.now()
        }

        ConversationResponse(
            id = newConvId.toString(),
            type = "DIRECT",
            name = null,
            participants = getParticipantsForConversationInternal(newConvId),
            lastMessage = null,
            unreadCount = 0,
            updatedAt = Clock.System.now().toString()
        )
    }

    suspend fun createGroupConversation(creatorId: UUID, name: String, memberIds: List<UUID>): ConversationResponse = dbQuery {
        val allMemberIds = (memberIds + creatorId).distinct()

        val newConvId = ConversationsTable.insertAndGetId {
            it[type] = "GROUP"
            it[this.name] = name
            it[createdAt] = Clock.System.now()
            it[updatedAt] = Clock.System.now()
        }.value

        allMemberIds.forEach { mId ->
            ConversationMembersTable.insert {
                it[conversationId] = newConvId
                it[userId] = mId
                it[joinedAt] = Clock.System.now()
                it[lastReadAt] = Clock.System.now()
            }
        }

        ConversationResponse(
            id = newConvId.toString(),
            type = "GROUP",
            name = name,
            participants = getParticipantsForConversationInternal(newConvId),
            lastMessage = null,
            unreadCount = 0,
            updatedAt = Clock.System.now().toString()
        )
    }

    suspend fun getMessagesForConversation(
        conversationId: UUID,
        currentUserId: UUID,
        limit: Int = 50,
        offset: Long = 0
    ): List<MessageResponse> = dbQuery {
        val rows = (MessagesTable innerJoin UsersTable)
            .selectAll()
            .where { MessagesTable.conversationId eq conversationId }
            .orderBy(MessagesTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .toList()
            .reversed()

        if (rows.isEmpty()) return@dbQuery emptyList()

        val messageIds = rows.map { it[MessagesTable.id].value }

        // Batch-load receipts for all messages in one query (avoids N+1 round trips).
        val receiptsByMessage = MessageReceiptsTable
            .selectAll()
            .where { MessageReceiptsTable.messageId inList messageIds }
            .groupBy({ it[MessageReceiptsTable.messageId].value }, { it[MessageReceiptsTable.userId].value to it[MessageReceiptsTable.status] })

        rows.map { row ->
            val msgId = row[MessagesTable.id].value
            val senderId = row[MessagesTable.senderId].value
            val senderName = row[UsersTable.fullName] ?: row[UsersTable.username]
            val type = row[MessagesTable.type]
            val textContent = row[MessagesTable.textContent]
            val mediaUrl = row[MessagesTable.mediaUrl]
            val sharedContentId = row[MessagesTable.sharedContentId]?.toString()
            val createdAt = row[MessagesTable.createdAt].toString()

            val status = calculateMessageStatusBulk(senderId, currentUserId, receiptsByMessage[msgId] ?: emptyList())

            MessageResponse(
                id = msgId.toString(),
                conversationId = conversationId.toString(),
                senderId = senderId.toString(),
                senderName = senderName,
                type = type,
                textContent = textContent,
                mediaUrl = mediaUrl,
                sharedContentId = sharedContentId,
                status = status,
                createdAt = createdAt
            )
        }
    }

    suspend fun saveMessage(
        conversationId: UUID,
        senderId: UUID,
        textContent: String?,
        type: String = "TEXT",
        mediaUrl: String? = null,
        sharedContentId: UUID? = null
    ): MessageResponse = dbQuery {
        val now = Clock.System.now()
        val newMsgId = MessagesTable.insertAndGetId {
            it[this.conversationId] = conversationId
            it[this.senderId] = senderId
            it[this.type] = type
            it[this.textContent] = textContent
            it[this.mediaUrl] = mediaUrl
            it[this.sharedContentId] = sharedContentId
            it[createdAt] = now
        }.value

        ConversationsTable.update({ ConversationsTable.id eq conversationId }) {
            it[updatedAt] = now
        }

        val recipients = ConversationMembersTable
            .select(ConversationMembersTable.userId)
            .where { 
                (ConversationMembersTable.conversationId eq conversationId) and 
                (ConversationMembersTable.userId neq senderId) 
            }
            .map { it[ConversationMembersTable.userId].value }

        recipients.forEach { recipientId ->
            MessageReceiptsTable.insert {
                it[messageId] = newMsgId
                it[userId] = recipientId
                it[status] = "SENT"
            }
        }
        val senderName = UsersTable
            .select(UsersTable.fullName, UsersTable.username)
            .where { UsersTable.id eq senderId }
            .map { it[UsersTable.fullName] ?: it[UsersTable.username] }
            .singleOrNull()

        MessageResponse(
            id = newMsgId.toString(),
            conversationId = conversationId.toString(),
            senderId = senderId.toString(),
            senderName = senderName,
            type = type,
            textContent = textContent,
            mediaUrl = mediaUrl,
            sharedContentId = sharedContentId?.toString(),
            status = "SENT",
            createdAt = now.toString()
        )
    }

    suspend fun updateMessageReceiptStatus(messageId: UUID, userId: UUID, status: String): Boolean = dbQuery {
        val now = Clock.System.now()
        val updated = MessageReceiptsTable.update({
            (MessageReceiptsTable.messageId eq messageId) and (MessageReceiptsTable.userId eq userId)
        }) {
            it[MessageReceiptsTable.status] = status
            if (status == "DELIVERED") {
                it[deliveredAt] = now
            } else if (status == "SEEN") {
                it[readAt] = now
                it[deliveredAt] = now
            }
        }
        updated > 0
    }

    /**
     * One UPDATE statement marks every recipient's receipt for a message DELIVERED, so
     * delivery notifications never cost one round trip per online recipient.
     */
    suspend fun markDeliveredForRecipients(messageId: UUID, userIds: List<UUID>): Int = dbQuery {
        if (userIds.isEmpty()) return@dbQuery 0
        val now = Clock.System.now()
        MessageReceiptsTable.update({
            (MessageReceiptsTable.messageId eq messageId) and
            (MessageReceiptsTable.userId inList userIds)
        }) {
            it[status] = "DELIVERED"
            it[deliveredAt] = now
        }
    }

    suspend fun isConversationMember(conversationId: UUID, userId: UUID): Boolean = dbQuery {
        ConversationMembersTable
            .select(ConversationMembersTable.userId)
            .where {
                (ConversationMembersTable.conversationId eq conversationId) and
                (ConversationMembersTable.userId eq userId)
            }
            .count() > 0
    }

    suspend fun markConversationAsRead(conversationId: UUID, userId: UUID): List<UUID> = dbQuery {
        val now = Clock.System.now()

        val unreadMsgIds = (MessageReceiptsTable innerJoin MessagesTable)
            .select(MessageReceiptsTable.messageId)
            .where {
                (MessagesTable.conversationId eq conversationId) and
                (MessageReceiptsTable.userId eq userId) and
                (MessageReceiptsTable.status neq "SEEN")
            }
            .map { it[MessageReceiptsTable.messageId].value }

        if (unreadMsgIds.isNotEmpty()) {
            MessageReceiptsTable.update({
                (MessageReceiptsTable.messageId inList unreadMsgIds) and
                (MessageReceiptsTable.userId eq userId)
            }) {
                it[status] = "SEEN"
                it[readAt] = now
            }
        }

        ConversationMembersTable.update({
            (ConversationMembersTable.conversationId eq conversationId) and
            (ConversationMembersTable.userId eq userId)
        }) {
            it[lastReadAt] = now
        }

        unreadMsgIds
    }

    suspend fun getConversationMemberIds(conversationId: UUID): List<UUID> = dbQuery {
        ConversationMembersTable
            .select(ConversationMembersTable.userId)
            .where { ConversationMembersTable.conversationId eq conversationId }
            .map { it[ConversationMembersTable.userId].value }
    }

    /**
     * Everyone who shares at least one conversation with [userId], in one round trip.
     *
     * Presence broadcasts fan out to exactly this set. Walking conversations first and then
     * asking for each one's members would cost ~170ms per conversation against the pooler.
     */
    suspend fun getPeerUserIds(userId: UUID): List<UUID> = rawRead { conn ->
        conn.prepareStatement(PEER_IDS_SQL).use { ps ->
            ps.setObject(1, userId)
            ps.setObject(2, userId)
            ps.executeQuery().use { rs ->
                val ids = mutableListOf<UUID>()
                while (rs.next()) ids += rs.getObject("user_id", UUID::class.java)
                ids
            }
        }
    }

    /**
     * Removes a message the caller sent. Returns false when it does not exist, belongs to
     * another sender, or is in another conversation — the caller answers 403/404 from that.
     *
     * Receipts disappear with it through the CASCADE on `message_receipts.message_id`.
     */
    suspend fun deleteMessage(conversationId: UUID, messageId: UUID, senderId: UUID): Boolean = dbQuery {
        val deleted = MessagesTable.deleteWhere {
            (MessagesTable.id eq messageId) and
            (MessagesTable.conversationId eq conversationId) and
            (MessagesTable.senderId eq senderId)
        }
        if (deleted > 0) {
            // The list is ordered by updatedAt, and the row's preview just changed.
            ConversationsTable.update({ ConversationsTable.id eq conversationId }) {
                it[updatedAt] = Clock.System.now()
            }
        }
        deleted > 0
    }

    suspend fun getPendingMessagesForUser(userId: UUID): List<Pair<UUID, MessageResponse>> = dbQuery {
        (MessageReceiptsTable innerJoin MessagesTable)
            .join(UsersTable, JoinType.INNER, onColumn = MessagesTable.senderId, otherColumn = UsersTable.id)
            .selectAll()
            .where {
                (MessageReceiptsTable.userId eq userId) and
                (MessageReceiptsTable.status eq "SENT")
            }
            .map { row ->
                val msgId = row[MessagesTable.id].value
                val convId = row[MessagesTable.conversationId].value
                val senderId = row[MessagesTable.senderId].value

                val msgResponse = MessageResponse(
                    id = msgId.toString(),
                    conversationId = convId.toString(),
                    senderId = senderId.toString(),
                    senderName = row[UsersTable.fullName] ?: row[UsersTable.username],
                    type = row[MessagesTable.type],
                    textContent = row[MessagesTable.textContent],
                    mediaUrl = row[MessagesTable.mediaUrl],
                    sharedContentId = row[MessagesTable.sharedContentId]?.toString(),
                    status = "DELIVERED",
                    createdAt = row[MessagesTable.createdAt].toString()
                )
                Pair(convId, msgResponse)
            }
    }

    private fun getParticipantsForConversationInternal(conversationId: UUID): List<ParticipantDto> {
        return (ConversationMembersTable innerJoin UsersTable)
            .leftJoin(ProfilesTable, onColumn = { UsersTable.id }, otherColumn = { ProfilesTable.id })
            .select(UsersTable.id, UsersTable.username, UsersTable.fullName, ProfilesTable.avatarUrl)
            .where { ConversationMembersTable.conversationId eq conversationId }
            .map { row ->
                ParticipantDto(
                    id = row[UsersTable.id].value.toString(),
                    username = row[UsersTable.username],
                    fullName = row[UsersTable.fullName],
                    avatarUrl = row[ProfilesTable.avatarUrl]
                )
            }
    }

    private fun getLastMessageForConversationInternal(conversationId: UUID, currentUserId: UUID): MessageResponse? {
        val lastMsgRow = (MessagesTable innerJoin UsersTable)
            .selectAll()
            .where { MessagesTable.conversationId eq conversationId }
            .orderBy(MessagesTable.createdAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull() ?: return null

        val msgId = lastMsgRow[MessagesTable.id].value
        val senderId = lastMsgRow[MessagesTable.senderId].value
        val senderName = lastMsgRow[UsersTable.fullName] ?: lastMsgRow[UsersTable.username]
        val type = lastMsgRow[MessagesTable.type]
        val textContent = lastMsgRow[MessagesTable.textContent]
        val mediaUrl = lastMsgRow[MessagesTable.mediaUrl]
        val sharedContentId = lastMsgRow[MessagesTable.sharedContentId]?.toString()
        val createdAt = lastMsgRow[MessagesTable.createdAt].toString()

        val status = calculateMessageStatusInternal(msgId, senderId, currentUserId)

        return MessageResponse(
            id = msgId.toString(),
            conversationId = conversationId.toString(),
            senderId = senderId.toString(),
            senderName = senderName,
            type = type,
            textContent = textContent,
            mediaUrl = mediaUrl,
            sharedContentId = sharedContentId,
            status = status,
            createdAt = createdAt
        )
    }

    private fun getUnreadCountInternal(conversationId: UUID, userId: UUID): Int {
        return (MessageReceiptsTable innerJoin MessagesTable)
            .selectAll()
            .where {
                (MessagesTable.conversationId eq conversationId) and
                (MessageReceiptsTable.userId eq userId) and
                (MessageReceiptsTable.status neq "SEEN") and
                (MessagesTable.senderId neq userId)
            }
            .count()
            .toInt()
    }

    private fun calculateMessageStatusBulk(
        senderId: UUID,
        currentUserId: UUID,
        receipts: List<Pair<UUID, String>>
    ): String {
        if (senderId == currentUserId) {
            if (receipts.isEmpty()) return "SENT"
            if (receipts.all { it.second == "SEEN" }) return "SEEN"
            if (receipts.any { it.second == "DELIVERED" || it.second == "SEEN" }) return "DELIVERED"
            return "SENT"
        } else {
            val userStatus = receipts.firstOrNull { it.first == currentUserId }?.second
            return userStatus ?: "SEEN"
        }
    }

    private fun calculateMessageStatusInternal(messageId: UUID, senderId: UUID, currentUserId: UUID): String {
        if (senderId == currentUserId) {
            val receipts = MessageReceiptsTable
                .select(MessageReceiptsTable.status)
                .where { MessageReceiptsTable.messageId eq messageId }
                .map { it[MessageReceiptsTable.status] }

            if (receipts.isEmpty()) return "SENT"
            if (receipts.all { it == "SEEN" }) return "SEEN"
            if (receipts.any { it == "DELIVERED" || it == "SEEN" }) return "DELIVERED"
            return "SENT"
        } else {
            val userStatus = MessageReceiptsTable
                .select(MessageReceiptsTable.status)
                .where { (MessageReceiptsTable.messageId eq messageId) and (MessageReceiptsTable.userId eq currentUserId) }
                .map { it[MessageReceiptsTable.status] }
                .singleOrNull()

            return userStatus ?: "SEEN"
        }
    }
}

/** Distinct users sharing a conversation with the given user (parameter bound twice). */
private val PEER_IDS_SQL: String = """
    SELECT DISTINCT cm.user_id AS user_id
      FROM conversation_members cm
     WHERE cm.conversation_id IN (
               SELECT conversation_id FROM conversation_members WHERE user_id = ?
           )
       AND cm.user_id <> ?
""".trimIndent()

/** One statement that returns the full conversation list (participants + last message + unread). */
private val CONVERSATION_LIST_SQL: String = """
    SELECT c.id AS conv_id, c.type AS conv_type, c.name AS conv_name, c.updated_at AS conv_updated_at,
           u.id AS p_id, u.username AS p_username, u.full_name AS p_full_name, pr.avatar_url AS p_avatar,
           lm.id AS lm_id, lm.sender_id AS lm_sender_id, lm.sender_name AS lm_sender_name,
           lm.type AS lm_type, lm.text_content AS lm_text, lm.media_url AS lm_media_url,
           lm.shared_content_id AS lm_shared_content_id,
           lm.created_at AS lm_created_at,
           lm.self_status AS lm_status,
           (SELECT count(*) FROM message_receipts mr2
              JOIN messages m2 ON m2.id = mr2.message_id
             WHERE m2.conversation_id = c.id AND mr2.user_id = ? AND mr2.status <> 'SEEN'
               AND m2.sender_id <> ?) AS unread_count
      FROM conversations c
      JOIN conversation_members cm ON cm.conversation_id = c.id
      JOIN users u ON u.id = cm.user_id
      LEFT JOIN profiles pr ON pr.user_id = u.id
      LEFT JOIN LATERAL (
          SELECT m.id, m.sender_id, m.type, m.text_content, m.media_url, m.shared_content_id, m.created_at,
                 COALESCE(su.full_name, su.username) AS sender_name,
                 CASE WHEN m.sender_id = ? THEN (
                             SELECT CASE
                                 WHEN count(*) = 0 THEN 'SENT'
                                 WHEN count(*) FILTER (WHERE mr.status = 'SEEN') = count(*) THEN 'SEEN'
                                 WHEN count(*) FILTER (WHERE mr.status IN ('DELIVERED','SEEN')) > 0 THEN 'DELIVERED'
                                 ELSE 'SENT' END
                             FROM message_receipts mr WHERE mr.message_id = m.id)
                      ELSE COALESCE((SELECT mr.status FROM message_receipts mr
                                      WHERE mr.message_id = m.id AND mr.user_id = ?), 'SEEN')
                 END AS self_status
            FROM messages m JOIN users su ON su.id = m.sender_id
           WHERE m.conversation_id = c.id
           ORDER BY m.created_at DESC LIMIT 1
      ) lm ON TRUE
     WHERE c.id IN (SELECT conversation_id FROM conversation_members WHERE user_id = ?)
     ORDER BY c.updated_at DESC, c.id
""".trimIndent()
