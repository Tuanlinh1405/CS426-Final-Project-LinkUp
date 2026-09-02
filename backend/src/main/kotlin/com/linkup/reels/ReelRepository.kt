package com.linkup.reels

import com.linkup.config.EnvConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** SQL and models for Reels stay out of the shared user/feed repositories. */
class ReelRepository(private val connect: () -> Connection = {
    val config = EnvConfig.database
    DriverManager.getConnection(config.jdbcUrl, config.connectionProperties())
}) {
    private suspend fun <T> transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        connect().use { connection ->
            connection.autoCommit = false
            try { block(connection).also { connection.commit() } }
            catch (error: Throwable) { connection.rollback(); throw error }
        }
    }

    suspend fun userExists(user: UUID): Boolean = transaction { db ->
        db.query("SELECT id FROM users WHERE id=?", user) { true }.isNotEmpty()
    }

    suspend fun candidates(viewer: UUID, author: UUID? = null): List<Candidate> = transaction { db ->
        val affinity = mutableMapOf<String, Double>()
        fun add(authorId: String, value: Double) { affinity[authorId] = (affinity[authorId] ?: 0.0) + value }
        db.query("SELECT r.author_id FROM reel_reactions l JOIN reels r ON r.id=l.reel_id WHERE l.user_id=?", viewer) {
            it.getString(1)
        }.forEach { add(it, 3.0) }
        db.query("SELECT DISTINCT r.author_id,c.reel_id FROM reel_comments c JOIN reels r ON r.id=c.reel_id WHERE c.author_id=?", viewer) {
            it.getString(1)
        }.forEach { add(it, 1.0) }
        db.query("SELECT r.author_id FROM reel_hidden h JOIN reels r ON r.id=h.reel_id WHERE h.user_id=?", viewer) {
            it.getString(1)
        }.forEach { add(it, -3.0) }
        db.query("""SELECT r.author_id,COALESCE(a.duration_ms,r.duration*1000,1000) duration_ms,MAX(w.watched_ms) watched,MAX(CASE WHEN w.skipped THEN 1 ELSE 0 END) skipped
            FROM reel_watch_events w JOIN reels r ON r.id=w.reel_id LEFT JOIN reel_assets a ON a.reel_id=r.id WHERE w.user_id=?
            GROUP BY r.author_id,r.id,r.duration,a.duration_ms""", viewer) {
            Triple(it.getString(1), it.getLong("watched") >= it.getLong("duration_ms").coerceAtLeast(1) * .8, it.getInt("skipped") == 1)
        }.forEach { (id, complete, skip) -> add(id, if (complete) 1.0 else if (skip) -1.0 else 0.0) }
        val following = db.query("SELECT following_id FROM follows WHERE follower_id=?", viewer) { it.getString(1) }.toSet()
        // One contribution per viewer/reel/day; loops and repeated event delivery do not inflate quality.
        val quality = db.query("""SELECT s.reel_id,COUNT(*) views,
            SUM(CASE WHEN s.watched >= COALESCE(a.duration_ms,r.duration*1000,1000) * .8 THEN 1 ELSE 0 END) completed
            FROM (SELECT reel_id,user_id,CAST(created_at AS DATE) event_day,MAX(watched_ms) watched
                FROM reel_watch_events WHERE watched_ms>0 GROUP BY reel_id,user_id,CAST(created_at AS DATE)) s
            JOIN reels r ON r.id=s.reel_id LEFT JOIN reel_assets a ON a.reel_id=r.id GROUP BY s.reel_id""") {
            it.getString(1) to ((it.getDouble("completed") + 2.0) / (it.getDouble("views") + 4.0))
        }.toMap()
        val seen = db.query("SELECT DISTINCT reel_id FROM reel_watch_events WHERE user_id=? AND watched_ms>0", viewer) { it.getString(1) }.toSet()
        val authorClause = if (author == null) "" else " AND r.author_id=?"
        val args = if (author == null) arrayOf<Any>(viewer, viewer) else arrayOf<Any>(viewer, viewer, author)
        db.query("$SELECT_REEL WHERE NOT EXISTS (SELECT 1 FROM reel_hidden h WHERE h.reel_id=r.id AND h.user_id=?)$authorClause ORDER BY r.created_at DESC,r.id DESC LIMIT 500", *args) { row ->
            val reel = row.reel()
            Candidate(reel, affinity[reel.author.id] ?: 0.0, reel.author.id in following,
                quality[reel.id] ?: 0.5, reel.id in seen)
        }
    }

    suspend fun get(id: UUID, viewer: UUID): ReelDto? = transaction { db ->
        db.query("$SELECT_REEL WHERE r.id=?", viewer, id) { it.reel() }.firstOrNull()
    }

    suspend fun visible(ids: List<String>, viewer: UUID): List<ReelDto> = transaction { db ->
        if (ids.isEmpty()) emptyList() else {
            val placeholders = ids.joinToString(",") { "?" }
            val rows = db.query("$SELECT_REEL WHERE r.id IN ($placeholders) AND NOT EXISTS (SELECT 1 FROM reel_hidden h WHERE h.reel_id=r.id AND h.user_id=?)",
                viewer, *ids.map(UUID::fromString).toTypedArray(), viewer) { it.reel() }.associateBy { it.id }
            ids.mapNotNull(rows::get)
        }
    }

    /** Returns false on an idempotent retry. Assets use unique keys so losing retries can be cleaned up. */
    suspend fun create(id: UUID, author: UUID, caption: String, metadata: VideoMetadata, asset: ReelAsset): Boolean = transaction { db ->
        val inserted = db.update("""INSERT INTO reels(id,author_id,caption,video_url,thumbnail_url,duration,width,height)
            VALUES(?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING""", id, author, caption, "reels/$id/video",
            asset.thumbnailKey?.let { "reels/$id/thumbnail" }, ((metadata.durationMs + 999) / 1000).toInt(), metadata.width, metadata.height)
        if (inserted == 0) {
            val owner = db.owner(id)
            if (owner != author) throw ReelFailure(409, "This upload identifier is already used.")
            false
        } else {
            db.update("INSERT INTO reel_assets(reel_id,video_key,thumbnail_key,storage_backend,file_size,duration_ms) VALUES(?,?,?,?,?,?)",
                id, asset.videoKey, asset.thumbnailKey, asset.storageBackend, asset.fileSize, metadata.durationMs)
            true
        }
    }

    suspend fun asset(id: UUID): ReelAsset? = transaction { db -> db.asset(id) }
    suspend fun delete(id: UUID, user: UUID): ReelAsset? = transaction { db ->
        if (db.owner(id) != user) throw ReelFailure(403, "Only the author can delete this reel.")
        val asset = db.asset(id)
        db.update("DELETE FROM reels WHERE id=? AND author_id=?", id, user)
        asset
    }

    suspend fun like(id: UUID, user: UUID, liked: Boolean) = transaction { db ->
        db.owner(id)
        if (liked) db.update("INSERT INTO reel_reactions(reel_id,user_id) VALUES(?,?) ON CONFLICT DO NOTHING", id, user)
        else db.update("DELETE FROM reel_reactions WHERE reel_id=? AND user_id=?", id, user)
        Unit
    }

    suspend fun hide(id: UUID, user: UUID, hidden: Boolean) = transaction { db ->
        db.owner(id)
        if (hidden) db.update("INSERT INTO reel_hidden(reel_id,user_id) VALUES(?,?) ON CONFLICT DO NOTHING", id, user)
        else db.update("DELETE FROM reel_hidden WHERE reel_id=? AND user_id=?", id, user)
        Unit
    }

    suspend fun comments(id: UUID, cursor: String?, limit: Int): CommentPage = transaction { db ->
        db.owner(id)
        val boundary = cursor?.let { parseCommentCursor(it) }
        val clause = if (boundary == null) "" else " AND (c.created_at < ? OR (c.created_at=? AND c.id<?))"
        val args = mutableListOf<Any>(id)
        boundary?.let { (time, commentId) -> args.addAll(listOf(time, time, commentId)) }
        args += limit + 1
        val comments = db.query("""SELECT c.id,c.content,c.created_at,u.id author_id,u.username,u.full_name,p.avatar_url
            FROM reel_comments c JOIN users u ON u.id=c.author_id LEFT JOIN profiles p ON p.user_id=u.id
            WHERE c.reel_id=?$clause ORDER BY c.created_at DESC,c.id DESC LIMIT ?""", *args.toTypedArray()) {
            ReelCommentDto(it.getString("id"), it.author(), it.getString("content"), it.getTimestamp("created_at").toInstant().toString())
        }
        val page = comments.take(limit)
        CommentPage(page, if (comments.size > limit) page.last().let { "${it.createdAt}|${it.id}" } else null)
    }

    suspend fun comment(id: UUID, user: UUID, request: AddComment): ReelCommentDto = transaction { db ->
        db.owner(id)
        val commentId = uuid(request.id)
        val content = request.content.trim()
        if (content.isEmpty() || content.length > 1000) throw ReelFailure(400, "Comment must contain 1–1000 characters.")
        db.update("INSERT INTO reel_comments(id,reel_id,author_id,content) VALUES(?,?,?,?) ON CONFLICT DO NOTHING", commentId, id, user, content)
        val rows = db.query("""SELECT c.id,c.reel_id,c.content,c.created_at,u.id author_id,u.username,u.full_name,p.avatar_url
            FROM reel_comments c JOIN users u ON u.id=c.author_id LEFT JOIN profiles p ON p.user_id=u.id WHERE c.id=?""", commentId) {
            if (it.getString("author_id") != user.toString() || it.getString("reel_id") != id.toString()) throw ReelFailure(409, "Comment identifier already used.")
            ReelCommentDto(it.getString("id"), it.author(), it.getString("content"), it.getTimestamp("created_at").toInstant().toString())
        }
        rows.single()
    }

    suspend fun deleteComment(reel: UUID, comment: UUID, user: UUID) = transaction { db ->
        val owner = db.query("SELECT author_id FROM reel_comments WHERE id=? AND reel_id=?", comment, reel) { it.getObject(1, UUID::class.java) }.firstOrNull()
            ?: throw ReelFailure(404, "Comment not found.")
        if (owner != user) throw ReelFailure(403, "Only the author can delete this comment.")
        db.update("DELETE FROM reel_comments WHERE id=? AND reel_id=? AND author_id=?", comment, reel, user)
        Unit
    }

    suspend fun watch(id: UUID, user: UUID, event: WatchEvent) = transaction { db ->
        val duration = db.query("SELECT COALESCE(a.duration_ms,r.duration*1000,1000) FROM reels r LEFT JOIN reel_assets a ON a.reel_id=r.id WHERE r.id=?", id) { it.getLong(1).coerceAtLeast(1) }.firstOrNull()
            ?: throw ReelFailure(404, "Reel not found.")
        if (event.watchedMs < 0 || event.reason !in setOf("START", "HEARTBEAT", "SWIPE", "PAUSE", "BACKGROUND")) throw ReelFailure(400, "Invalid playback event.")
        val eventId = uuid(event.id)
        db.update("INSERT INTO reel_watch_events(id,reel_id,user_id) VALUES(?,?,?) ON CONFLICT DO NOTHING", eventId, id, user)
        val state = db.query("SELECT user_id,reel_id,created_at,watched_ms,skipped FROM reel_watch_events WHERE id=? FOR UPDATE", eventId) {
            if (it.getObject("user_id", UUID::class.java) != user || it.getObject("reel_id", UUID::class.java) != id) throw ReelFailure(409, "Playback session already used.")
            Triple(it.getTimestamp("created_at").toInstant(), it.getLong("watched_ms"), it.getBoolean("skipped"))
        }.single()
        val elapsed = (Instant.now().toEpochMilli() - state.first.toEpochMilli() + 2000).coerceAtLeast(0)
        val replayLimit = if (duration > Long.MAX_VALUE / 3) Long.MAX_VALUE else duration * 3
        val watched = maxOf(state.second, minOf(event.watchedMs, replayLimit, elapsed))
        db.update("UPDATE reel_watch_events SET watched_ms=?,skipped=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            watched, watched in 1..1999 && (state.third || event.reason == "SWIPE"), eventId)
        Unit
    }

    private fun Connection.owner(id: UUID): UUID = query("SELECT author_id FROM reels WHERE id=?", id) { it.getObject(1, UUID::class.java) }.firstOrNull()
        ?: throw ReelFailure(404, "Reel not found.")
    private fun Connection.asset(id: UUID): ReelAsset? = query("SELECT a.* FROM reel_assets a JOIN reels r ON r.id=a.reel_id WHERE a.reel_id=?", id) {
        ReelAsset(it.getString("video_key"), it.getString("thumbnail_key"), it.getString("storage_backend"), it.getLong("file_size"))
    }.firstOrNull()
    private fun ResultSet.author() = ReelAuthor(getString("author_id"), getString("username"), getString("full_name")?.takeIf(String::isNotBlank) ?: getString("username"), getString("avatar_url"))
    private fun ResultSet.reel() = ReelDto(getString("id"), author(), getString("caption") ?: "", getString("video_url"), getString("thumbnail_url"),
        getLong("duration_ms"), getInt("width"), getInt("height"), getTimestamp("created_at").toInstant().toString(), getLong("likes"), getLong("comments"), getBoolean("liked"))

    companion object {
        private const val SELECT_REEL = """SELECT r.*,u.username,u.full_name,p.avatar_url,COALESCE(a.duration_ms,r.duration*1000,1000) duration_ms,
            (SELECT COUNT(*) FROM reel_reactions l WHERE l.reel_id=r.id) likes,
            (SELECT COUNT(*) FROM reel_comments c WHERE c.reel_id=r.id) comments,
            EXISTS(SELECT 1 FROM reel_reactions l WHERE l.reel_id=r.id AND l.user_id=?) liked
            FROM reels r JOIN users u ON u.id=r.author_id LEFT JOIN profiles p ON p.user_id=u.id LEFT JOIN reel_assets a ON a.reel_id=r.id"""
        fun uuid(value: String?): UUID = try { UUID.fromString(value) } catch (_: Exception) { throw ReelFailure(400, "Invalid identifier.") }
        private fun parseCommentCursor(value: String): Pair<Timestamp, UUID> = try {
            val parts = value.split('|'); require(parts.size == 2)
            Timestamp.from(Instant.parse(parts[0])) to UUID.fromString(parts[1])
        } catch (_: Exception) { throw ReelFailure(400, "Invalid comments cursor.") }
    }
}

internal fun Connection.update(sql: String, vararg args: Any?): Int = prepareStatement(sql).use { statement ->
    args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }; statement.executeUpdate()
}
internal fun <T> Connection.query(sql: String, vararg args: Any?, mapper: (ResultSet) -> T): List<T> = prepareStatement(sql).use { statement ->
    statement.queryTimeout = 20
    args.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
    statement.executeQuery().use { result -> buildList { while (result.next()) add(mapper(result)) } }
}
