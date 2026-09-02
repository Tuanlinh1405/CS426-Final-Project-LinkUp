package com.linkup.reels

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ReelRankerTest {
    private val now = Instant.parse("2026-08-31T00:00:00Z")
    private fun candidate(id: String, author: String, affinity: Double = 0.0, seen: Boolean = false) = Candidate(
        ReelDto(id, ReelAuthor(author, author, author), "", "video", durationMs = 10000, width = 720, height = 1280,
            createdAt = now.toString(), likeCount = 0, commentCount = 0, liked = false), affinity, false, .5, seen,
    )
    @Test fun `positive interactions outrank neutral and negative signals`() {
        val ranked = ReelRanker.rank(listOf(candidate("neutral", "a"), candidate("liked", "b", 6.0), candidate("skipped", "c", -6.0)), now)
        assertEquals(listOf("liked", "neutral", "skipped"), ranked)
    }
    @Test fun `viewed items lose priority and duplicates are removed`() {
        val unseen = candidate("new", "a")
        assertEquals(listOf("new", "seen"), ReelRanker.rank(listOf(candidate("seen", "a", seen = true), unseen, unseen), now))
    }
    @Test fun `avoids three consecutive reels by same creator when alternatives exist`() {
        val rows = (1..4).map { candidate("a$it", "a", 12.0) } + (1..4).map { candidate("b$it", "b") }
        val ranked = ReelRanker.rank(rows, now)
        val firstThree = ranked.take(3).map { id -> rows.single { it.reel.id == id }.reel.author.id }
        assertTrue(firstThree.toSet().size > 1)
    }
}
