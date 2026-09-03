package com.example.linkup.feature.dating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeDatingRepositoryTest {
    @Test
    fun `candidate who liked current user is prioritized`() {
        val repository = FakeDatingRepository(
            initialCandidates = listOf(
                DatingCandidate(
                    user = com.example.linkup.data.model.User("u2", "Emma Chen", "@emma", "EC"),
                    age = 25,
                    bio = "Photographer and city explorer.",
                    interests = listOf("Travel", "Coffee"),
                    likedYou = true
                ),
                DatingCandidate(
                    user = com.example.linkup.data.model.User("u3", "Amelia Wong", "@amelia", "AW"),
                    age = 27,
                    bio = "Product designer.",
                    interests = listOf("Design")
                )
            )
        )

        assertEquals("u2", repository.getDiscoverCandidates().first().user.id)
        assertTrue(repository.getDiscoverCandidates().first().likedYou)
    }

    @Test
    fun `pass hides candidate until passed candidates are reset`() {
        val repository = FakeDatingRepository()

        repository.swipe("u3", SwipeDecision.PASS)
        assertFalse(repository.getDiscoverCandidates().any { it.user.id == "u3" })

        repository.resetPassedCandidates()
        assertTrue(repository.getDiscoverCandidates().any { it.user.id == "u3" })
    }

    @Test
    fun `reviewing passed candidates does not bring back liked candidate`() {
        val repository = FakeDatingRepository()

        repository.swipe("u2", SwipeDecision.LIKE)
        repository.swipe("u3", SwipeDecision.PASS)
        repository.resetPassedCandidates()

        val visibleIds = repository.getDiscoverCandidates().map { it.user.id }
        assertFalse(visibleIds.contains("u2"))
        assertTrue(visibleIds.contains("u3"))
    }

    @Test
    fun `empty discover list is returned after all candidates are passed`() {
        val repository = FakeDatingRepository()

        repository.swipe("u2", SwipeDecision.PASS)
        repository.swipe("u3", SwipeDecision.PASS)
        repository.swipe("u4", SwipeDecision.PASS)

        assertTrue(repository.getDiscoverCandidates().isEmpty())
    }

    @Test
    fun `mutual like creates one match`() {
        val repository = FakeDatingRepository(
            initialCandidates = listOf(
                DatingCandidate(
                    user = com.example.linkup.data.model.User("u2", "Emma Chen", "@emma", "EC"),
                    age = 25,
                    bio = "Photographer and city explorer.",
                    interests = listOf("Travel", "Coffee"),
                    likedYou = true
                )
            )
        )

        val firstLike = repository.swipe("u2", SwipeDecision.LIKE)
        val repeatedLike = repository.swipe("u2", SwipeDecision.LIKE)

        assertTrue(firstLike.isMatch)
        assertTrue(repeatedLike.isMatch)
        assertEquals(1, repository.getMatches().size)
    }

    @Test
    fun `one sided like does not create match`() {
        val repository = FakeDatingRepository(
            initialCandidates = listOf(
                DatingCandidate(
                    user = com.example.linkup.data.model.User("u9", "Alex", "@alex", "AX"),
                    age = 26,
                    bio = "Likes hiking.",
                    interests = listOf("Travel")
                )
            )
        )

        val result = repository.swipe("u9", SwipeDecision.LIKE)

        assertFalse(result.isMatch)
        assertTrue(repository.getMatches().isEmpty())
    }
}
