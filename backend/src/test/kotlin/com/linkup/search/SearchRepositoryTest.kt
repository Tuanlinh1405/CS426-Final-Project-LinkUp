package com.linkup.search

import com.linkup.reels.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.sql.DriverManager
import java.util.UUID

class SearchRepositoryTest {
    @Test fun `search ranks people and finds matching posts and reels`() = runBlocking {
        val url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        fun connect() = DriverManager.getConnection(url, "sa", "")
        val alice = UUID.randomUUID(); val bob = UUID.randomUUID(); val post = UUID.randomUUID(); val reel = UUID.randomUUID()
        connect().use { db ->
            val schema = """
                CREATE TABLE users(id UUID PRIMARY KEY,username VARCHAR(50),full_name VARCHAR(100));
                CREATE TABLE profiles(user_id UUID PRIMARY KEY,avatar_url TEXT,bio TEXT,follower_count INT DEFAULT 0);
                CREATE TABLE posts(id UUID PRIMARY KEY,author_id UUID,content TEXT,privacy_level VARCHAR(20),created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP);
                CREATE TABLE media(id UUID PRIMARY KEY,storage_key TEXT,width INT,height INT);
                CREATE TABLE post_media(post_id UUID,media_id UUID,display_order INT,PRIMARY KEY(post_id,media_id));
                CREATE TABLE post_reactions(post_id UUID,user_id UUID,PRIMARY KEY(post_id,user_id));
                CREATE TABLE comments(id UUID PRIMARY KEY,post_id UUID);
                CREATE TABLE reels(id UUID PRIMARY KEY,author_id UUID,caption TEXT,video_url TEXT,thumbnail_url TEXT,duration INT,created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP);
                CREATE TABLE reel_assets(reel_id UUID PRIMARY KEY,video_key TEXT,thumbnail_key TEXT,storage_backend TEXT,duration_ms BIGINT);
                CREATE TABLE reel_reactions(reel_id UUID,user_id UUID,PRIMARY KEY(reel_id,user_id));
                CREATE TABLE reel_comments(id UUID PRIMARY KEY,reel_id UUID);
                CREATE TABLE reel_hidden(reel_id UUID,user_id UUID,PRIMARY KEY(reel_id,user_id));
            """.trimIndent()
            schema.split(';').filter(String::isNotBlank).forEach { sql -> db.createStatement().use { it.execute(sql) } }
            db.update("INSERT INTO users(id,username,full_name) VALUES(?,?,?)", alice, "alice", "Alice Football")
            db.update("INSERT INTO users(id,username,full_name) VALUES(?,?,?)", bob, "bob", "Bob")
            db.update("INSERT INTO profiles(user_id,bio,follower_count) VALUES(?,?,?)", alice, "Football creator", 50)
            db.update("INSERT INTO profiles(user_id,bio,follower_count) VALUES(?,?,?)", bob, "Games", 5)
            db.update("INSERT INTO posts(id,author_id,content,privacy_level) VALUES(?,?,?,?)", post, alice, "Professional football highlights", "PUBLIC")
            db.update("INSERT INTO reels(id,author_id,caption,video_url,duration) VALUES(?,?,?,?,?)", reel, alice, "Football skills", "reels/$reel/video", 60)
            db.update("INSERT INTO reel_assets(reel_id,video_key,storage_backend,duration_ms) VALUES(?,?,?,?)", reel, "reels/$alice/$reel/video.mp4", "local", 60_000)
        }
        val repository = SearchRepository { connect() }
        val all = repository.search(bob, "football", "all", null, 20)
        assertEquals(post.toString(), all.posts.single().id)
        assertEquals(reel.toString(), all.reels.single().id)
        assertEquals(alice.toString(), all.people.single().id)
        assertNull(all.nextCursor)
        val exact = repository.search(bob, "alice", "people", null, 1)
        assertEquals("alice", exact.people.single().username)
        connect().use { it.createStatement().use { statement -> statement.execute("SHUTDOWN") } }
        Unit
    }
}
