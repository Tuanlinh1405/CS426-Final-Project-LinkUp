package com.linkup.reels

import java.sql.DriverManager
import java.util.UUID

class ReelTestDatabase {
    private val url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    val repository = ReelRepository { connect() }
    val alice = UUID.randomUUID()
    val bob = UUID.randomUUID()
    fun connect() = DriverManager.getConnection(url, "sa", "")
    init {
        connect().use { db ->
            val schema = """
                CREATE TABLE users(id UUID PRIMARY KEY, username VARCHAR(50), full_name VARCHAR(100));
                CREATE TABLE profiles(user_id UUID PRIMARY KEY REFERENCES users(id), avatar_url TEXT);
                CREATE TABLE follows(follower_id UUID, following_id UUID, PRIMARY KEY(follower_id,following_id));
                CREATE TABLE reels(id UUID PRIMARY KEY,author_id UUID NOT NULL REFERENCES users(id),caption TEXT,
                    video_url TEXT NOT NULL,thumbnail_url TEXT,duration INT,width INT,height INT,
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP);
            """.trimIndent()
            schema.split(';').filter(String::isNotBlank).forEach { db.createStatement().use { statement -> statement.execute(it) } }
            // Test the same additive SQL as PostgreSQL, excluding Supabase-specific grants/RLS.
            val migration = javaClass.getResource("/db/migrations/001_reels_interactions.sql")!!.readText()
                .substringBefore("-- These feature tables").replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE")
            migration.lineSequence().filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
                .split(';').filter(String::isNotBlank).forEach { db.createStatement().use { statement -> statement.execute(it) } }
            val durationMigration = javaClass.getResource("/db/migrations/003_reels_unbounded_duration.sql")!!.readText()
            durationMigration.lineSequence().filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
                .split(';').filter(String::isNotBlank).forEach { db.createStatement().use { statement -> statement.execute(it) } }
            db.update("INSERT INTO users(id,username,full_name) VALUES(?,?,?)", alice, "alice", "Alice")
            db.update("INSERT INTO users(id,username,full_name) VALUES(?,?,?)", bob, "bob", "Bob")
            db.commit()
        }
    }
    fun close() { connect().use { it.createStatement().use { s -> s.execute("SHUTDOWN") } } }
}
