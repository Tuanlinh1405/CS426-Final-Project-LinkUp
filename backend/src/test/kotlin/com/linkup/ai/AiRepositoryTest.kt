package com.linkup.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AiRepositoryTest {
    private val databaseUrl = "jdbc:h2:mem:ai-repository;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    private val userId = UUID.randomUUID()
    private lateinit var repository: AiRepository

    @BeforeEach
    fun setUp() {
        DriverManager.getConnection(databaseUrl).use { db ->
            db.createStatement().use { sql ->
                sql.execute("DROP ALL OBJECTS")
                sql.execute("CREATE TABLE users(id UUID PRIMARY KEY)")
                sql.execute("CREATE TABLE ai_conversations(id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id),title TEXT,created_at TIMESTAMP WITH TIME ZONE,updated_at TIMESTAMP WITH TIME ZONE)")
                sql.execute("CREATE TABLE ai_messages(id UUID PRIMARY KEY,ai_conversation_id UUID NOT NULL REFERENCES ai_conversations(id),role VARCHAR(20),content TEXT,created_at TIMESTAMP WITH TIME ZONE)")
                sql.execute("CREATE TABLE ai_analysis_cache(fingerprint VARCHAR(64) PRIMARY KEY,answer TEXT,created_at TIMESTAMP WITH TIME ZONE)")
                sql.execute("INSERT INTO users(id) VALUES('$userId')")
            }
        }
        repository = AiRepository { DriverManager.getConnection(databaseUrl) }
    }

    @Test
    fun `background result is appended to the pending conversation`() = runBlocking {
        val started = repository.startAnalysis(userId, "Analyze post", "Analyze this post", null)
        assertEquals(listOf("user"), started.messages.map { it.role })

        repository.appendAnalysisResult(UUID.fromString(started.conversation.id), "Finished analysis")

        val messages = repository.messages(UUID.fromString(started.conversation.id), userId)
        assertEquals(listOf("user", "model"), messages.map { it.role })
        assertEquals("Finished analysis", messages.last().content)
    }

    @Test
    fun `cached answer is reused immediately`() = runBlocking {
        assertNull(repository.cachedAnalysis("fingerprint"))
        repository.cacheAnalysis("fingerprint", "Cached result")
        repository.cacheAnalysis("fingerprint", "Must not replace")

        val cached = repository.cachedAnalysis("fingerprint")
        val started = repository.startAnalysis(userId, "Analyze post", "Analyze this post", cached)

        assertEquals("Cached result", cached)
        assertEquals(listOf("user", "model"), started.messages.map { it.role })
        assertEquals("Cached result", started.messages.last().content)
    }
}
