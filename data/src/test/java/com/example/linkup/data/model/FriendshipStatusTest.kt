package com.example.linkup.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendshipStatusTest {

    @Test
    fun `parses every status the backend sends`() {
        assertEquals(FriendshipStatus.NONE, FriendshipStatus.from("NONE"))
        assertEquals(FriendshipStatus.REQUEST_SENT, FriendshipStatus.from("REQUEST_SENT"))
        assertEquals(FriendshipStatus.REQUEST_RECEIVED, FriendshipStatus.from("REQUEST_RECEIVED"))
        assertEquals(FriendshipStatus.FRIENDS, FriendshipStatus.from("FRIENDS"))
    }

    @Test
    fun `parsing is case-insensitive`() {
        assertEquals(FriendshipStatus.FRIENDS, FriendshipStatus.from("friends"))
        assertEquals(FriendshipStatus.REQUEST_SENT, FriendshipStatus.from("Request_Sent"))
    }

    @Test
    fun `an unknown or missing status degrades instead of crashing`() {
        assertEquals(FriendshipStatus.UNKNOWN, FriendshipStatus.from("BLOCKED"))
        assertEquals(FriendshipStatus.UNKNOWN, FriendshipStatus.from(""))
        assertEquals(FriendshipStatus.UNKNOWN, FriendshipStatus.from(null))
    }

    @Test
    fun `only FRIENDS counts as an actual friendship`() {
        assertTrue(FriendshipStatus.FRIENDS.isFriend)
        listOf(
            FriendshipStatus.NONE,
            FriendshipStatus.REQUEST_SENT,
            FriendshipStatus.REQUEST_RECEIVED,
            FriendshipStatus.UNKNOWN
        ).forEach { assertFalse("$it should not be a friendship", it.isFriend) }
    }

    @Test
    fun `friend notifications lead to the person who acted`() {
        fun notification(type: NotificationType) = Notification(
            id = "n1",
            type = type,
            actor = NotificationActor("u2", "ben", "Ben", null),
            targetId = "u2",
            isRead = false,
            createdAt = "2026-09-02T12:00:00Z"
        )

        assertEquals("u2", notification(NotificationType.FRIEND_REQUEST).destinationUserId)
        assertEquals("u2", notification(NotificationType.FRIEND_ACCEPT).destinationUserId)
    }
}
