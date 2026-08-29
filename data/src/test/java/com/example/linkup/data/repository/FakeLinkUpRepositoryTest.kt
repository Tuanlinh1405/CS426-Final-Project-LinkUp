package com.example.linkup.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeLinkUpRepositoryTest {
    @Test
    fun `createPost inserts new post at top`() {
        val repository = FakeLinkUpRepository()
        val created = repository.createPost("A new LinkUp post")

        assertEquals(created.id, repository.feed().first().id)
        assertEquals("A new LinkUp post", repository.feed().first().content)
    }

    @Test
    fun `toggleLike is reversible`() {
        val repository = FakeLinkUpRepository()
        val original = repository.feed().first()
        val liked = repository.toggleLike(original.id).first()
        val unliked = repository.toggleLike(original.id).first()

        assertTrue(liked.liked)
        assertEquals(original.likes + 1, liked.likes)
        assertFalse(unliked.liked)
        assertEquals(original.likes, unliked.likes)
    }

    @Test
    fun `blank messages are not added`() {
        val repository = FakeLinkUpRepository()
        val initialCount = repository.messages().size

        repository.sendMessage("   ")

        assertEquals(initialCount, repository.messages().size)
    }
}
