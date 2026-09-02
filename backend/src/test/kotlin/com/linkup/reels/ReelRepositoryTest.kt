package com.linkup.reels

import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class ReelRepositoryTest {
    private lateinit var database: ReelTestDatabase
    @Before fun setup() { database = ReelTestDatabase() }
    @After fun teardown() { database.close() }
    private suspend fun create(owner: UUID = database.alice, durationMs: Long = 10000): UUID {
        val id = UUID.randomUUID()
        assertTrue(database.repository.create(id, owner, "Test", VideoMetadata(durationMs, 720, 1280), ReelAsset("reels/$owner/$id/${UUID.randomUUID()}.mp4", null, "local", 100)))
        return id
    }

    @Test fun `likes and comment retries are idempotent and persisted`() = runBlocking {
        val id = create(); val repo = database.repository
        repo.like(id, database.bob, true); repo.like(id, database.bob, true)
        assertEquals(1, repo.get(id, database.bob)!!.likeCount)
        assertTrue(repo.get(id, database.bob)!!.liked)
        val comment = AddComment(UUID.randomUUID().toString(), "Hello")
        repo.comment(id, database.bob, comment); repo.comment(id, database.bob, comment)
        assertEquals(1, repo.get(id, database.bob)!!.commentCount)
        assertEquals("Hello", repo.comments(id, null, 10).items.single().content)
        repo.like(id, database.bob, false); repo.like(id, database.bob, false)
        assertEquals(0, repo.get(id, database.bob)!!.likeCount)
    }

    @Test fun `ownership protects reel and comment deletion`() = runBlocking {
        val id = create(); val repo = database.repository
        val comment = repo.comment(id, database.bob, AddComment(UUID.randomUUID().toString(), "Mine"))
        try { repo.delete(id, database.bob); fail("Must reject non-owner") } catch (e: ReelFailure) { assertEquals(403, e.status) }
        try { repo.deleteComment(id, UUID.fromString(comment.id), database.alice); fail("Must reject non-owner") } catch (e: ReelFailure) { assertEquals(403, e.status) }
        repo.deleteComment(id, UUID.fromString(comment.id), database.bob)
        repo.delete(id, database.alice)
        assertNull(repo.get(id, database.bob)); assertNull(repo.asset(id))
        assertEquals(0, database.connect().use { it.query("SELECT COUNT(*) FROM reel_comments") { r -> r.getInt(1) }.single() })
    }

    @Test fun `comment cursor and feed snapshots do not duplicate rows`() = runBlocking {
        val repo = database.repository
        val ids = (1..5).map { create() }
        repeat(4) { repo.comment(ids[0], database.bob, AddComment(UUID.randomUUID().toString(), "Comment $it")) }
        val first = repo.comments(ids[0], null, 2)
        val second = repo.comments(ids[0], first.nextCursor, 2)
        assertEquals(4, (first.items + second.items).map { it.id }.toSet().size)
        val feed = ReelFeed(repo)
        val page1 = feed.page(database.bob, null, null, 2)
        repo.like(ids.last(), database.bob, true)
        val page2 = feed.page(database.bob, null, page1.nextCursor, 3)
        assertEquals(5, (page1.items + page2.items).map { it.id }.toSet().size)
        try { feed.page(database.alice, null, page1.nextCursor, 2); fail("Cursor must belong to viewer") } catch (e: ReelFailure) { assertEquals(400, e.status) }
    }

    @Test fun `hidden reels disappear and watch sessions cannot be hijacked`() = runBlocking {
        val repo = database.repository; val id = create(); val event = UUID.randomUUID().toString()
        repo.watch(id, database.bob, WatchEvent(event, 0, "START"))
        database.connect().use { it.update("UPDATE reel_watch_events SET created_at=? WHERE id=?", Timestamp.from(Instant.now().minusSeconds(30)), UUID.fromString(event)) }
        repo.watch(id, database.bob, WatchEvent(event, 9000))
        repo.watch(id, database.bob, WatchEvent(event, 1000))
        assertEquals(9000, database.connect().use { it.query("SELECT watched_ms FROM reel_watch_events") { r -> r.getLong(1) }.single() })
        try { repo.watch(id, database.alice, WatchEvent(event, 1000)); fail("Must bind event to user") } catch (e: ReelFailure) { assertEquals(409, e.status) }
        assertTrue(repo.candidates(database.bob).single().affinity > 0)
        repo.hide(id, database.bob, true)
        assertTrue(repo.candidates(database.bob).isEmpty())
        val other = create()
        assertEquals(other.toString(), repo.candidates(database.bob).single().reel.id)
        assertEquals(-2.0, repo.candidates(database.bob).single().affinity, 0.001)
        repo.hide(id, database.bob, false)
        assertEquals(2, repo.candidates(database.bob).size)
        assertTrue(repo.candidates(database.bob).all { it.affinity == 1.0 })
    }

    @Test fun `long reels and watch events are not capped at three minutes`() = runBlocking {
        val repo = database.repository
        val id = create(durationMs = 600000)
        val event = UUID.randomUUID()
        repo.watch(id, database.bob, WatchEvent(event.toString(), 0, "START"))
        database.connect().use { it.update("UPDATE reel_watch_events SET created_at=? WHERE id=?", Timestamp.from(Instant.now().minusSeconds(300)), event) }
        repo.watch(id, database.bob, WatchEvent(event.toString(), 240000, "HEARTBEAT"))
        assertEquals(240000, database.connect().use { it.query("SELECT watched_ms FROM reel_watch_events WHERE id=?", event) { row -> row.getLong(1) }.single() })
    }
}
