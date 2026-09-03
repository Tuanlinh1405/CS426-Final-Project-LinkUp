package com.linkup.ai

import com.linkup.database.DatabaseFactory
import com.linkup.reels.query
import com.linkup.reels.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class AiRepository(private val connect: () -> Connection = DatabaseFactory::connection) {
    private suspend fun <T> transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        connect().use { db ->
            db.autoCommit = false
            try { block(db).also { db.commit() } }
            catch (error: Throwable) { db.rollback(); throw error }
        }
    }

    private suspend fun <T> read(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        connect().use { db ->
            db.autoCommit = true
            block(db)
        }
    }

    suspend fun createConversation(userId: UUID, requestedTitle: String?): AiConversationDto = transaction { db ->
        val id = UUID.randomUUID()
        val now = Timestamp.from(Instant.now())
        val title = requestedTitle?.trim()?.take(100)?.takeIf(String::isNotBlank) ?: "Trò chuyện với LinkUp AI"
        db.update(
            "INSERT INTO ai_conversations(id,user_id,title,created_at,updated_at) VALUES(?,?,?,?,?)",
            id, userId, title, now, now,
        )
        AiConversationDto(id.toString(), title, null, now.toInstant().toString(), now.toInstant().toString())
    }

    suspend fun startAnalysis(userId: UUID, title: String, prompt: String, cachedAnswer: String?): AiAnalysisResponse = transaction { db ->
        val conversationId = UUID.randomUUID()
        val now = Timestamp.from(Instant.now())
        db.update(
            "INSERT INTO ai_conversations(id,user_id,title,created_at,updated_at) VALUES(?,?,?,?,?)",
            conversationId, userId, title, now, now,
        )
        val userMessage = db.insertMessage(conversationId, "user", prompt, now)
        val modelMessage = cachedAnswer?.let { db.insertMessage(conversationId, "model", it, Timestamp.from(Instant.now())) }
        AiAnalysisResponse(
            conversation = AiConversationDto(
                conversationId.toString(),
                title,
                modelMessage?.content ?: userMessage.content,
                now.toInstant().toString(),
                modelMessage?.createdAt ?: userMessage.createdAt,
            ),
            messages = listOfNotNull(userMessage, modelMessage),
        )
    }

    suspend fun appendAnalysisResult(conversationId: UUID, answer: String): AiMessageDto = transaction { db ->
        val now = Timestamp.from(Instant.now())
        val message = db.insertMessage(conversationId, "model", answer, now)
        db.update("UPDATE ai_conversations SET updated_at=? WHERE id=?", now, conversationId)
        message
    }

    suspend fun cachedAnalysis(fingerprint: String): String? = read { db ->
        db.query("SELECT answer FROM ai_analysis_cache WHERE fingerprint=?", fingerprint) { it.getString("answer") }
            .firstOrNull()
    }

    suspend fun cacheAnalysis(fingerprint: String, answer: String) = transaction { db ->
        db.update(
            """INSERT INTO ai_analysis_cache(fingerprint,answer,created_at)
               SELECT ?,?,? WHERE NOT EXISTS
               (SELECT 1 FROM ai_analysis_cache WHERE fingerprint=?)""",
            fingerprint,
            answer,
            Timestamp.from(Instant.now()),
            fingerprint,
        )
        Unit
    }

    suspend fun conversations(userId: UUID): List<AiConversationDto> = read { db ->
        db.query(
            """SELECT c.id,c.title,c.created_at,c.updated_at,
                      (SELECT content FROM ai_messages m WHERE m.ai_conversation_id=c.id ORDER BY m.created_at DESC,m.id DESC LIMIT 1) last_message
                 FROM ai_conversations c WHERE c.user_id=? ORDER BY c.updated_at DESC,c.id DESC""",
            userId,
        ) { row ->
            AiConversationDto(
                id = row.getString("id"),
                title = row.getString("title")?.takeIf(String::isNotBlank) ?: "Cuộc trò chuyện mới",
                lastMessage = row.getString("last_message"),
                createdAt = row.instant("created_at"),
                updatedAt = row.instant("updated_at"),
            )
        }
    }

    suspend fun messages(conversationId: UUID, userId: UUID): List<AiMessageDto> = read { db ->
        val messages = db.query(
            """SELECT m.id,m.ai_conversation_id,m.role,m.content,m.created_at
                 FROM ai_messages m JOIN ai_conversations c ON c.id=m.ai_conversation_id
                WHERE m.ai_conversation_id=? AND c.user_id=? ORDER BY m.created_at,m.id""",
            conversationId,
            userId,
        ) { it.aiMessage() }
        if (messages.isEmpty()) db.requireOwner(conversationId, userId)
        messages
    }

    suspend fun appendExchange(
        conversationId: UUID,
        userId: UUID,
        prompt: String,
        answer: String,
    ): List<AiMessageDto> = transaction { db ->
        db.requireOwner(conversationId, userId)
        val userMessage = db.insertMessage(conversationId, "user", prompt, Timestamp.from(Instant.now()))
        val modelMessage = db.insertMessage(conversationId, "model", answer, Timestamp.from(Instant.now()))
        db.update("UPDATE ai_conversations SET updated_at=? WHERE id=?", Timestamp.from(Instant.now()), conversationId)
        listOf(userMessage, modelMessage)
    }

    private fun Connection.requireOwner(conversationId: UUID, userId: UUID) {
        if (query("SELECT id FROM ai_conversations WHERE id=? AND user_id=?", conversationId, userId) { true }.isEmpty()) {
            throw AiFailure(404, "Không tìm thấy cuộc trò chuyện AI.")
        }
    }

    private fun Connection.insertMessage(conversationId: UUID, role: String, content: String, createdAt: Timestamp): AiMessageDto {
        val id = UUID.randomUUID()
        update(
            "INSERT INTO ai_messages(id,ai_conversation_id,role,content,created_at) VALUES(?,?,?,?,?)",
            id, conversationId, role, content, createdAt,
        )
        return AiMessageDto(id.toString(), conversationId.toString(), role, content, createdAt.toInstant().toString())
    }

    private fun ResultSet.aiMessage() = AiMessageDto(
        id = getString("id"),
        conversationId = getString("ai_conversation_id"),
        role = getString("role"),
        content = getString("content"),
        createdAt = instant("created_at"),
    )

    private fun ResultSet.instant(column: String): String = getTimestamp(column).toInstant().toString()
}
