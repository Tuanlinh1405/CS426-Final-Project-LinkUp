package com.example.linkup.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationModelsTest {

    private fun actor(full: String? = "Alex Chen", username: String = "alex.c") =
        NotificationActor(id = "u2", username = username, fullName = full, avatarUrl = null)

    private fun notification(
        type: NotificationType = NotificationType.FOLLOW,
        target: String? = "u2"
    ) = Notification(
        id = "n1",
        type = type,
        actor = actor(),
        targetId = target,
        isRead = false,
        createdAt = "2026-09-02T12:00:00Z"
    )

    @Test
    fun `unknown types degrade instead of failing`() {
        assertEquals(NotificationType.UNKNOWN, NotificationType.from("POKE"))
        assertEquals(NotificationType.UNKNOWN, NotificationType.from(""))
        assertEquals(NotificationType.FOLLOW, NotificationType.from("follow"))
        assertEquals(NotificationType.DATING_MATCH, NotificationType.from("DATING_MATCH"))
    }

    @Test
    fun `actor falls back to the username when there is no full name`() {
        assertEquals("Alex Chen", actor().displayName)
        assertEquals("alex.c", actor(full = null).displayName)
        assertEquals("alex.c", actor(full = "  ").displayName)
    }

    @Test
    fun `initials take at most two letters and never come back empty`() {
        assertEquals("AC", actor().initials)
        assertEquals("AC", actor(full = null).initials)
        assertEquals("N", actor(full = "Nguyen").initials)
    }

    @Test
    fun `follow notifications navigate to the actor`() {
        assertEquals("u2", notification().destinationUserId)
        // Falls back to the actor when the backend sent no target.
        assertEquals("u2", notification(target = null).destinationUserId)
    }

    @Test
    fun `other types have no destination yet`() {
        assertNull(notification(type = NotificationType.LIKE).destinationUserId)
        assertNull(notification(type = NotificationType.SYSTEM).destinationUserId)
        assertNull(notification(type = NotificationType.UNKNOWN).destinationUserId)
    }

    @Test
    fun `system notices are flagged so they are not written as self-actions`() {
        assertTrue(notification(type = NotificationType.SYSTEM).isSystem)
        assertFalse(notification(type = NotificationType.FOLLOW).isSystem)
    }
}
