package com.linkup.reels

import java.time.Instant
import kotlin.math.exp

/** Small-data heuristic, not a trained model. No topic/tag/category input. */
object ReelRanker {
    fun score(candidate: Candidate, now: Instant): Double {
        val ageDays = ((now.epochSecond - Instant.parse(candidate.reel.createdAt).epochSecond).coerceAtLeast(0)) / 86400.0
        val affinity = (candidate.affinity / 12.0).coerceIn(-1.0, 1.0)
        return .35 * affinity + .25 * candidate.quality.coerceIn(0.0, 1.0) +
            .20 * exp(-ageDays / 7.0) + (if (candidate.following) .20 else 0.0) - (if (candidate.seen) .45 else 0.0)
    }

    fun rank(candidates: List<Candidate>, now: Instant = Instant.now()): List<String> {
        val remaining = candidates.distinctBy { it.reel.id }.sortedWith(
            compareByDescending<Candidate> { score(it, now) }.thenBy { it.reel.id },
        ).toMutableList()
        val result = mutableListOf<Candidate>()
        while (remaining.isNotEmpty()) {
            val lastAuthors = result.takeLast(2).map { it.reel.author.id }
            val diverse = remaining.filter { lastAuthors.size < 2 || lastAuthors.any { author -> author != it.reel.author.id } }
            val pool = diverse.ifEmpty { remaining }
            val next = if (result.size % 5 == 4) {
                pool.filter { !it.seen }.maxByOrNull { it.reel.createdAt } ?: pool.first()
            } else pool.first()
            result += next
            remaining.remove(next)
        }
        return result.map { it.reel.id }
    }
}
