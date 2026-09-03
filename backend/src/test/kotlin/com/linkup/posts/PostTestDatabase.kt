package com.linkup.posts

import com.linkup.reels.update
import java.sql.DriverManager
import java.util.UUID

class PostTestDatabase {
    private val url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
    val repository = PostRepository { connect() }
    val alice = UUID.randomUUID()
    val bob = UUID.randomUUID()
    fun connect() = DriverManager.getConnection(url, "sa", "")
    init {
        connect().use { db ->
            val schema = """
                CREATE TABLE users(id UUID PRIMARY KEY,username VARCHAR(50),full_name VARCHAR(100));
                CREATE TABLE profiles(user_id UUID PRIMARY KEY REFERENCES users(id),avatar_url TEXT);
                CREATE TABLE posts(id UUID PRIMARY KEY,author_id UUID NOT NULL REFERENCES users(id),content TEXT,privacy_level VARCHAR(20) DEFAULT 'PUBLIC',created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP);
                CREATE TABLE media(id UUID PRIMARY KEY,owner_id UUID NOT NULL REFERENCES users(id),storage_key TEXT UNIQUE,mime_type VARCHAR(100),file_size BIGINT,width INT,height INT,created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP);
                CREATE TABLE post_media(post_id UUID REFERENCES posts(id) ON DELETE CASCADE,media_id UUID REFERENCES media(id) ON DELETE CASCADE,display_order INT DEFAULT 0,PRIMARY KEY(post_id,media_id));
                CREATE TABLE comments(id UUID PRIMARY KEY,post_id UUID REFERENCES posts(id) ON DELETE CASCADE,author_id UUID REFERENCES users(id),content TEXT,parent_comment_id UUID REFERENCES comments(id) ON DELETE CASCADE,created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP);
                CREATE TABLE comment_reactions(comment_id UUID REFERENCES comments(id) ON DELETE CASCADE,user_id UUID REFERENCES users(id),created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(comment_id,user_id));
                CREATE TABLE post_reactions(post_id UUID REFERENCES posts(id) ON DELETE CASCADE,user_id UUID REFERENCES users(id),type VARCHAR(20) DEFAULT 'LIKE',created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(post_id,user_id));
                CREATE TABLE notifications(id UUID PRIMARY KEY,recipient_id UUID REFERENCES users(id),actor_id UUID REFERENCES users(id),type VARCHAR(50),target_id UUID,is_read BOOLEAN DEFAULT FALSE,created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP);
            """.trimIndent()
            schema.split(';').filter(String::isNotBlank).forEach { sql -> db.createStatement().use { it.execute(sql) } }
            db.update("INSERT INTO users(id,username,full_name) VALUES(?,?,?)", alice, "alice", "Alice")
            db.update("INSERT INTO users(id,username,full_name) VALUES(?,?,?)", bob, "bob", "Bob")
        }
    }
    fun close() { connect().use { it.createStatement().use { statement -> statement.execute("SHUTDOWN") } } }
}
