package com.linkup.reels

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Stable, user-bound snapshots: interactions affect the next refresh, not cursor order mid-scroll. */
class ReelFeed(private val repository: ReelRepository) {
    private data class Session(val user: UUID, val author: UUID?, val ids: List<String>, val expiresAt: Instant)
    private val sessions = ConcurrentHashMap<UUID, Session>()

    suspend fun page(user: UUID, author: UUID?, cursor: String?, limit: Int): ReelPage {
        val now = Instant.now()
        sessions.entries.removeIf { it.value.expiresAt.isBefore(now) }
        val sessionId: UUID
        val offset: Int
        val session: Session
        if (cursor == null) {
            val candidates = repository.candidates(user, author)
            val ids = if (author == null) ReelRanker.rank(candidates, now) else candidates.map { it.reel.id }
            sessionId = UUID.randomUUID()
            offset = 0
            session = Session(user, author, ids, now.plusSeconds(1800))
            if (sessions.size >= 500) sessions.entries.minByOrNull { it.value.expiresAt }?.let { sessions.remove(it.key) }
            sessions[sessionId] = session
        } else {
            val parts = cursor.split(':')
            if (parts.size != 2) throw ReelFailure(400, "Invalid feed cursor.")
            sessionId = ReelRepository.uuid(parts[0])
            offset = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: throw ReelFailure(400, "Invalid feed cursor.")
            session = sessions[sessionId] ?: throw ReelFailure(410, "Feed expired. Refresh to continue.")
            if (session.user != user || session.author != author || offset > session.ids.size) throw ReelFailure(400, "Invalid feed cursor.")
        }
        val items = mutableListOf<ReelDto>()
        var position = offset
        while (items.size < limit && position < session.ids.size) {
            val batch = session.ids.drop(position).take(limit - items.size)
            items += repository.visible(batch, user)
            position += batch.size
        }
        return ReelPage(items, if (position < session.ids.size) "$sessionId:$position" else null)
    }
}
