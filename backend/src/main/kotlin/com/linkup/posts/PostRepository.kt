package com.linkup.posts

import com.linkup.config.EnvConfig
import com.linkup.reels.query
import com.linkup.reels.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostRepository(private val connect: () -> Connection = {
    val config = EnvConfig.database
    DriverManager.getConnection(config.jdbcUrl, config.connectionProperties())
}) {
    private suspend fun <T> transaction(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        connect().use { db ->
            db.autoCommit = false
            try { block(db).also { db.commit() } }
            catch (error: Throwable) { db.rollback(); throw error }
        }
    }

    suspend fun userExists(user: UUID): Boolean = transaction { db ->
        db.query("SELECT id FROM users WHERE id=?", user) { true }.isNotEmpty()
    }

    suspend fun page(viewer: UUID, cursor: String?, limit: Int): PostPage = transaction { db ->
        val boundary = cursor?.let(::parseCursor)
        val clause = if (boundary == null) "" else " AND (p.created_at < ? OR (p.created_at=? AND p.id<?))"
        val args = mutableListOf<Any>(viewer)
        boundary?.let { (time, id) -> args.addAll(listOf(time, time, id)) }
        args += limit + 1
        val rows = db.query("$SELECT_POST WHERE p.privacy_level='PUBLIC'$clause ORDER BY p.created_at DESC,p.id DESC LIMIT ?", *args.toTypedArray()) { it.rawPost() }
        val pageRows = rows.take(limit)
        val mediaByPost = db.media(pageRows.map { uuid(it.id) })
        val page = pageRows.map { it.toDto(mediaByPost[it.id].orEmpty()) }
        PostPage(page, if (rows.size > limit) rows[limit - 1].let { "${it.createdAt}|${it.id}" } else null)
    }

    suspend fun get(id: UUID, viewer: UUID): PostDto? = transaction { db ->
        db.query("$SELECT_POST WHERE p.id=? AND (p.privacy_level='PUBLIC' OR p.author_id=?)", viewer, id, viewer) { it.rawPost() }
            .firstOrNull()?.let { it.toDto(db.media(listOf(uuid(it.id)))[it.id].orEmpty()) }
    }

    suspend fun create(id: UUID, author: UUID, content: String, media: List<NewPostMedia>): Boolean = transaction { db ->
        val clean = content.trim()
        if (clean.length > 5000) throw PostFailure(400, "Post content is limited to 5000 characters.")
        if (clean.isEmpty() && media.isEmpty()) throw PostFailure(400, "Write something or add a photo.")
        if (media.size > 4) throw PostFailure(400, "A post can contain up to 4 photos.")
        val inserted = db.update("INSERT INTO posts(id,author_id,content,privacy_level) VALUES(?,?,?,?) ON CONFLICT DO NOTHING", id, author, clean, "PUBLIC")
        if (inserted == 0) {
            if (db.owner(id) != author) throw PostFailure(409, "Post identifier already used.")
            return@transaction false
        }
        media.forEachIndexed { index, item ->
            db.update("INSERT INTO media(id,owner_id,storage_key,mime_type,file_size) VALUES(?,?,?,?,?)", item.id, author, item.storageKey, item.mimeType, item.fileSize)
            db.update("INSERT INTO post_media(post_id,media_id,display_order) VALUES(?,?,?)", id, item.id, index)
        }
        true
    }

    suspend fun like(id: UUID, user: UUID, liked: Boolean): PostDto = transaction { db ->
        val owner = db.owner(id)
        if (liked) {
            val inserted = db.update("INSERT INTO post_reactions(post_id,user_id,type) VALUES(?,?,?) ON CONFLICT DO NOTHING", id, user, "LIKE")
            if (inserted > 0 && owner != user) db.notification(owner, user, "POST_LIKE", id)
        } else {
            db.update("DELETE FROM post_reactions WHERE post_id=? AND user_id=?", id, user)
            db.update("DELETE FROM notifications WHERE recipient_id=? AND actor_id=? AND type='POST_LIKE' AND target_id=?", owner, user, id)
        }
        db.query("$SELECT_POST WHERE p.id=?", user, id) { it.rawPost() }.single().let {
            it.toDto(db.media(listOf(id))[it.id].orEmpty())
        }
    }

    suspend fun comments(id: UUID, viewer: UUID, cursor: String?, limit: Int): PostCommentPage = transaction { db ->
        db.visibleOwner(id, viewer)
        val boundary = cursor?.let(::parseCursor)
        val clause = if (boundary == null) "" else " AND (c.created_at < ? OR (c.created_at=? AND c.id<?))"
        val args = mutableListOf<Any>(id)
        boundary?.let { (time, commentId) -> args.addAll(listOf(time, time, commentId)) }
        args += limit + 1
        val rows = db.query("""SELECT c.id,c.content,c.created_at,u.id author_id,u.username,u.full_name,p.avatar_url
            FROM comments c JOIN users u ON u.id=c.author_id LEFT JOIN profiles p ON p.user_id=u.id
            WHERE c.post_id=?$clause ORDER BY c.created_at DESC,c.id DESC LIMIT ?""", *args.toTypedArray()) {
            PostCommentDto(it.getString("id"), it.author(), it.getString("content"), it.getTimestamp("created_at").toInstant().toString())
        }
        val page = rows.take(limit)
        PostCommentPage(page, if (rows.size > limit) page.last().let { "${it.createdAt}|${it.id}" } else null)
    }

    suspend fun comment(id: UUID, user: UUID, request: AddPostComment): PostCommentDto = transaction { db ->
        val owner = db.visibleOwner(id, user)
        val commentId = uuid(request.id)
        val content = request.content.trim()
        if (content.isEmpty() || content.length > 1000) throw PostFailure(400, "Comment must contain 1–1000 characters.")
        val inserted = db.update("INSERT INTO comments(id,post_id,author_id,content) VALUES(?,?,?,?) ON CONFLICT DO NOTHING", commentId, id, user, content)
        val result = db.query("""SELECT c.id,c.post_id,c.content,c.created_at,u.id author_id,u.username,u.full_name,p.avatar_url
            FROM comments c JOIN users u ON u.id=c.author_id LEFT JOIN profiles p ON p.user_id=u.id WHERE c.id=?""", commentId) {
            if (it.getString("author_id") != user.toString() || it.getString("post_id") != id.toString()) throw PostFailure(409, "Comment identifier already used.")
            PostCommentDto(it.getString("id"), it.author(), it.getString("content"), it.getTimestamp("created_at").toInstant().toString())
        }.single()
        if (inserted > 0 && owner != user) db.notification(owner, user, "POST_COMMENT", id)
        result
    }

    suspend fun deleteComment(post: UUID, comment: UUID, user: UUID) = transaction { db ->
        val owner = db.query("SELECT author_id FROM comments WHERE id=? AND post_id=?", comment, post) { it.getObject(1, UUID::class.java) }.firstOrNull()
            ?: throw PostFailure(404, "Comment not found.")
        if (owner != user) throw PostFailure(403, "Only the author can delete this comment.")
        db.update("DELETE FROM comments WHERE id=? AND post_id=? AND author_id=?", comment, post, user)
        Unit
    }

    suspend fun delete(id: UUID, user: UUID): List<StoredPostMedia> = transaction { db ->
        if (db.owner(id) != user) throw PostFailure(403, "Only the author can delete this post.")
        val media = db.query("""SELECT m.id,m.storage_key,m.mime_type,m.file_size FROM media m
            JOIN post_media pm ON pm.media_id=m.id WHERE pm.post_id=?""", id) {
            StoredPostMedia(it.getObject("id", UUID::class.java), it.getString("storage_key"), it.getString("mime_type") ?: "application/octet-stream", it.getLong("file_size"))
        }
        db.update("DELETE FROM posts WHERE id=? AND author_id=?", id, user)
        media.forEach { db.update("DELETE FROM media WHERE id=?", it.id) }
        media
    }

    suspend fun media(id: UUID): StoredPostMedia? = transaction { db ->
        db.query("SELECT id,storage_key,mime_type,file_size FROM media WHERE id=?", id) {
            StoredPostMedia(it.getObject("id", UUID::class.java), it.getString("storage_key"), it.getString("mime_type") ?: "application/octet-stream", it.getLong("file_size"))
        }.firstOrNull()
    }

    private fun Connection.owner(id: UUID): UUID = query("SELECT author_id FROM posts WHERE id=?", id) { it.getObject(1, UUID::class.java) }.firstOrNull()
        ?: throw PostFailure(404, "Post not found.")
    private fun Connection.visibleOwner(id: UUID, viewer: UUID): UUID = query("SELECT author_id FROM posts WHERE id=? AND (privacy_level='PUBLIC' OR author_id=?)", id, viewer) { it.getObject(1, UUID::class.java) }.firstOrNull()
        ?: throw PostFailure(404, "Post not found.")
    private fun Connection.notification(recipient: UUID, actor: UUID, type: String, target: UUID) {
        update("INSERT INTO notifications(id,recipient_id,actor_id,type,target_id) VALUES(?,?,?,?,?)", UUID.randomUUID(), recipient, actor, type, target)
    }
    private fun Connection.media(posts: List<UUID>): Map<String, List<PostMediaDto>> {
        if (posts.isEmpty()) return emptyMap()
        val placeholders = posts.joinToString(",") { "?" }
        return query("""SELECT pm.post_id,m.id,m.mime_type,m.width,m.height,m.storage_key FROM media m
            JOIN post_media pm ON pm.media_id=m.id WHERE pm.post_id IN ($placeholders)
            ORDER BY pm.post_id,pm.display_order,m.id""", *posts.toTypedArray()) {
            it.getString("post_id") to PostMediaDto(
                it.getString("id"),
                "media/${it.getString("id")}",
                it.getString("mime_type") ?: "application/octet-stream",
                it.getObject("width") as? Int,
                it.getObject("height") as? Int,
                it.getString("storage_key"),
            )
        }.groupBy({ it.first }, { it.second })
    }
    private fun ResultSet.author() = PostAuthor(getString("author_id"), getString("username"), getString("full_name")?.takeIf(String::isNotBlank) ?: getString("username"), getString("avatar_url"))
    private fun ResultSet.rawPost() = RawPost(getString("id"), author(), getString("content") ?: "", getString("privacy_level"),
        getTimestamp("created_at").toInstant().toString(), getTimestamp("updated_at").toInstant().toString(), getLong("likes"), getLong("comments"), getBoolean("liked"))

    private data class RawPost(
        val id: String, val author: PostAuthor, val content: String, val privacy: String,
        val createdAt: String, val updatedAt: String, val likes: Long, val comments: Long, val liked: Boolean,
    ) {
        fun toDto(media: List<PostMediaDto>) = PostDto(id, author, content, privacy, createdAt, updatedAt, media, likes, comments, liked)
    }

    companion object {
        private const val SELECT_POST = """SELECT p.*,u.username,u.full_name,pr.avatar_url,
            (SELECT COUNT(*) FROM post_reactions r WHERE r.post_id=p.id) likes,
            (SELECT COUNT(*) FROM comments c WHERE c.post_id=p.id) comments,
            EXISTS(SELECT 1 FROM post_reactions r WHERE r.post_id=p.id AND r.user_id=?) liked
            FROM posts p JOIN users u ON u.id=p.author_id LEFT JOIN profiles pr ON pr.user_id=u.id"""
        fun uuid(value: String?): UUID = try { UUID.fromString(value) } catch (_: Exception) { throw PostFailure(400, "Invalid identifier.") }
        private fun parseCursor(value: String): Pair<Timestamp, UUID> = try {
            val parts = value.split('|'); require(parts.size == 2)
            Timestamp.from(Instant.parse(parts[0])) to UUID.fromString(parts[1])
        } catch (_: Exception) { throw PostFailure(400, "Invalid cursor.") }
    }
}
