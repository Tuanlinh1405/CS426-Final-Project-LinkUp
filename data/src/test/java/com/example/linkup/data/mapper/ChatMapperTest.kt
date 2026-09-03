package com.example.linkup.data.mapper

import com.example.linkup.data.model.MessageStatus
import com.example.linkup.data.remote.dto.ConversationDto
import com.example.linkup.data.remote.dto.MessageDto
import com.example.linkup.data.remote.dto.ParticipantDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMapperTest {

    @Test
    fun testParticipantDtoToDomain() {
        val dto = ParticipantDto("u1", "alex", "Alex Chen", "http://avatar.url")
        val domain = dto.toDomain()

        assertEquals("u1", domain.id)
        assertEquals("alex", domain.username)
        assertEquals("Alex Chen", domain.displayName)
        assertEquals("AC", domain.initials)
    }

    @Test
    fun testMessageDtoToDomainFromMe() {
        val dto = MessageDto(
            id = "m1",
            conversationId = "c1",
            senderId = "u1",
            senderName = "Alex",
            type = "TEXT",
            textContent = "Hello World",
            status = "DELIVERED",
            createdAt = "2026-09-01T10:15:00.000Z"
        )

        val messageFromMe = dto.toDomain(currentUserId = "u1")
        assertTrue("Message from current user should have fromMe = true", messageFromMe.fromMe)
        assertEquals(MessageStatus.DELIVERED, messageFromMe.status)

        val messageFromOther = dto.toDomain(currentUserId = "u2")
        assertFalse("Message from other user should have fromMe = false", messageFromOther.fromMe)
    }

    @Test
    fun testConversationDtoToDomain() {
        val participant1 = ParticipantDto("u1", "alice", "Alice Smith")
        val participant2 = ParticipantDto("u2", "bob", "Bob Jones")

        val lastMsg = MessageDto(
            id = "m1",
            conversationId = "c1",
            senderId = "u2",
            textContent = "See you tomorrow!",
            status = "SEEN",
            createdAt = "2026-09-01T14:30:00.000Z"
        )

        val dto = ConversationDto(
            id = "c1",
            type = "DIRECT",
            participants = listOf(participant1, participant2),
            lastMessage = lastMsg,
            unreadCount = 3,
            updatedAt = "2026-09-01T14:30:00.000Z"
        )

        // Viewing as Alice (u1) -> title should be Bob Jones (u2)
        val domainForAlice = dto.toDomain(currentUserId = "u1")
        assertEquals("Bob Jones", domainForAlice.user.name)
        assertEquals("See you tomorrow!", domainForAlice.preview)
        assertEquals(3, domainForAlice.unread)
        assertEquals("14:30", domainForAlice.time)
    }
}
