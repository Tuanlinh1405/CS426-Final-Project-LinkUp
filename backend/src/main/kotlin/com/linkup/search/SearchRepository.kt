package com.linkup.search

import com.linkup.database.DatabaseFactory
import com.linkup.reels.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

class SearchRepository(private val connect: () -> Connection = DatabaseFactory::connection) {
    suspend fun search(viewer: UUID, rawQuery: String, type: String, rawCursor: String?, requestedLimit: Int): SearchResults =
        withContext(Dispatchers.IO) {
            val query = rawQuery.trim().replace(Regex("\\s+"), " ")
            if (query.length !in 2..100) throw SearchFailure(400, "Search needs 2–100 characters.")
            if (type !in setOf("all", "people", "posts", "reels")) throw SearchFailure(400, "Invalid search type.")
            val offset = rawCursor?.toIntOrNull()?.takeIf { it in 0..10_000 }
                ?: if (rawCursor == null) 0 else throw SearchFailure(400, "Invalid search cursor.")
            val limit = if (type == "all") minOf(5, requestedLimit) else requestedLimit.coerceIn(1, 30)
            connect().use { db ->
                val people = if (type in setOf("all", "people")) db.people(query, offset, limit + 1) else emptyList()
                val posts = if (type in setOf("all", "posts")) db.posts(viewer, query, offset, limit + 1) else emptyList()
                val reels = if (type in setOf("all", "reels")) db.reels(viewer, query, offset, limit + 1) else emptyList()
                val hasMore = when (type) {
                    "people" -> people.size > limit
                    "posts" -> posts.size > limit
                    "reels" -> reels.size > limit
                    else -> false
                }
                SearchResults(
                    people.take(limit),
                    posts.take(limit),
                    reels.take(limit),
                    if (hasMore) (offset + limit).toString() else null,
                )
            }
        }

    private fun Connection.people(term: String, offset: Int, limit: Int): List<SearchPerson> {
        val contains = like(term, both = true)
        val prefix = like(term, both = false)
        return query(
            """SELECT u.id,u.username,u.full_name,p.avatar_url,p.bio,COALESCE(p.follower_count,0) followers
            FROM users u LEFT JOIN profiles p ON p.user_id=u.id
            WHERE LOWER(u.username) LIKE ? ESCAPE '!' OR LOWER(COALESCE(u.full_name,'')) LIKE ? ESCAPE '!'
            ORDER BY CASE WHEN LOWER(u.username)=? THEN 0 WHEN LOWER(u.username) LIKE ? ESCAPE '!' THEN 1 ELSE 2 END,
            COALESCE(p.follower_count,0) DESC,u.username ASC LIMIT ? OFFSET ?""",
            contains, contains, term.lowercase(), prefix, limit, offset,
        ) { it.person() }
    }

    private fun Connection.posts(viewer: UUID, term: String, offset: Int, limit: Int): List<SearchPost> {
        val contains = like(term, both = true)
        val prefix = like(term, both = false)
        return query(
            """SELECT p.id,p.content,p.created_at,u.id author_id,u.username,u.full_name,pr.avatar_url,pr.bio,
            COALESCE(pr.follower_count,0) followers,
            (SELECT COUNT(*) FROM post_reactions r WHERE r.post_id=p.id) likes,
            (SELECT COUNT(*) FROM comments c WHERE c.post_id=p.id) comments,
            (SELECT m.id FROM post_media pm JOIN media m ON m.id=pm.media_id WHERE pm.post_id=p.id ORDER BY pm.display_order,m.id LIMIT 1) image_id,
            (SELECT m.storage_key FROM post_media pm JOIN media m ON m.id=pm.media_id WHERE pm.post_id=p.id ORDER BY pm.display_order,m.id LIMIT 1) image_key
            FROM posts p JOIN users u ON u.id=p.author_id LEFT JOIN profiles pr ON pr.user_id=u.id
            WHERE (p.privacy_level='PUBLIC' OR p.author_id=?) AND
            (LOWER(COALESCE(p.content,'')) LIKE ? ESCAPE '!' OR LOWER(u.username) LIKE ? ESCAPE '!' OR LOWER(COALESCE(u.full_name,'')) LIKE ? ESCAPE '!')
            ORDER BY CASE WHEN LOWER(COALESCE(p.content,'')) LIKE ? ESCAPE '!' THEN 0 ELSE 1 END,p.created_at DESC,p.id DESC LIMIT ? OFFSET ?""",
            viewer, contains, contains, contains, prefix, limit, offset,
        ) {
            SearchPost(
                it.getString("id"), it.person("author_id"), it.getString("content") ?: "",
                it.getTimestamp("created_at").toInstant().toString(), it.getString("image_id"),
                it.getString("image_id")?.let { id -> "media/$id" }, it.getLong("likes"), it.getLong("comments"),
                it.getString("image_key"),
            )
        }
    }

    private fun Connection.reels(viewer: UUID, term: String, offset: Int, limit: Int): List<SearchReel> {
        val contains = like(term, both = true)
        val prefix = like(term, both = false)
        return query(
            """SELECT r.id,r.caption,r.video_url,r.thumbnail_url,r.created_at,u.id author_id,u.username,u.full_name,p.avatar_url,p.bio,
            COALESCE(p.follower_count,0) followers,a.video_key,a.thumbnail_key,a.storage_backend,
            COALESCE(a.duration_ms,r.duration*1000,0) duration_ms,
            (SELECT COUNT(*) FROM reel_reactions x WHERE x.reel_id=r.id) likes,
            (SELECT COUNT(*) FROM reel_comments c WHERE c.reel_id=r.id) comments
            FROM reels r JOIN users u ON u.id=r.author_id LEFT JOIN profiles p ON p.user_id=u.id LEFT JOIN reel_assets a ON a.reel_id=r.id
            WHERE NOT EXISTS (SELECT 1 FROM reel_hidden h WHERE h.reel_id=r.id AND h.user_id=?) AND
            (LOWER(COALESCE(r.caption,'')) LIKE ? ESCAPE '!' OR LOWER(u.username) LIKE ? ESCAPE '!' OR LOWER(COALESCE(u.full_name,'')) LIKE ? ESCAPE '!')
            ORDER BY CASE WHEN LOWER(COALESCE(r.caption,'')) LIKE ? ESCAPE '!' THEN 0 ELSE 1 END,r.created_at DESC,r.id DESC LIMIT ? OFFSET ?""",
            viewer, contains, contains, contains, prefix, limit, offset,
        ) {
            SearchReel(
                it.getString("id"), it.person("author_id"), it.getString("caption") ?: "",
                it.getTimestamp("created_at").toInstant().toString(), it.getString("video_url"), it.getString("thumbnail_url"),
                it.getLong("duration_ms"), it.getLong("likes"), it.getLong("comments"), it.getString("video_key"),
                it.getString("thumbnail_key"), it.getString("storage_backend"),
            )
        }
    }

    private fun ResultSet.person(idColumn: String = "id") = SearchPerson(
        getString(idColumn), getString("username"), getString("full_name")?.takeIf(String::isNotBlank) ?: getString("username"),
        getString("avatar_url"), getString("bio"), getLong("followers"),
    )

    private fun like(value: String, both: Boolean): String {
        val escaped = value.lowercase().replace("!", "!!").replace("%", "!%").replace("_", "!_")
        return if (both) "%$escaped%" else "$escaped%"
    }
}
