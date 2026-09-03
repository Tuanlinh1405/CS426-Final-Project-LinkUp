package com.example.linkup.data.mapper

import com.example.linkup.data.model.MessageStatus
import com.example.linkup.data.remote.dto.ConversationDto
import com.example.linkup.data.remote.dto.MessageDto
import com.example.linkup.data.remote.dto.ParticipantDto
import com.example.linkup.data.util.ChatTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        val participant2 = ParticipantDto("u2", "bob", "Bob Jones", "http://bob.png")

        // Today, so the list stamp is a clock time rather than a weekday or a date.
        val createdAt = ChatTime.nowIso()

        val lastMsg = MessageDto(
            id = "m1",
            conversationId = "c1",
            senderId = "u2",
            textContent = "See you tomorrow!",
            status = "SEEN",
            createdAt = createdAt
        )

        val dto = ConversationDto(
            id = "c1",
            type = "DIRECT",
            participants = listOf(participant1, participant2),
            lastMessage = lastMsg,
            unreadCount = 3,
            updatedAt = createdAt
        )

        // Viewing as Alice (u1) -> title should be Bob Jones (u2)
        val domainForAlice = dto.toDomain(currentUserId = "u1")
        assertEquals("Bob Jones", domainForAlice.user.name)
        assertEquals("See you tomorrow!", domainForAlice.preview)
        assertEquals(3, domainForAlice.unread)
        assertEquals("http://bob.png", domainForAlice.user.avatarUrl)
        assertEquals(listOf("u2"), domainForAlice.others.map { it.id })

        val expectedClock = SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(ChatTime.parseMillis(createdAt)!!))
        assertEquals(expectedClock, domainForAlice.time)
    }

    @Test
    fun testGroupConversationShowsSenderInPreview() {
        val me = ParticipantDto("u1", "alice", "Alice Smith")
        val bob = ParticipantDto("u2", "bob", "Bob Jones")
        val carol = ParticipantDto("u3", "carol", "Carol White")

        val lastMsg = MessageDto(
            id = "m9",
            conversationId = "g1",
            senderId = "u3",
            senderName = "Carol White",
            textContent = "Đi ăn không?",
            createdAt = ChatTime.nowIso()
        )

        val group = ConversationDto(
            id = "g1",
            type = "GROUP",
            name = "CS426 Team",
            participants = listOf(me, bob, carol),
            lastMessage = lastMsg,
            updatedAt = ChatTime.nowIso()
        )

        val domain = group.toDomain(currentUserId = "u1")
        assertTrue("GROUP type should map to isGroup", domain.isGroup)
        assertEquals("CS426 Team", domain.user.name)
        assertEquals("Carol White: Đi ăn không?", domain.preview)
        assertEquals(listOf("u2", "u3"), domain.others.map { it.id })
        assertEquals("3 thành viên", domain.user.username)
    }

    @Test
    fun testGroupPreviewForOwnMessage() {
        val me = ParticipantDto("u1", "alice", "Alice Smith")
        val bob = ParticipantDto("u2", "bob", "Bob Jones")

        val group = ConversationDto(
            id = "g2",
            type = "GROUP",
            name = "Nhóm 2",
            participants = listOf(me, bob),
            lastMessage = MessageDto(
                id = "m1",
                conversationId = "g2",
                senderId = "u1",
                textContent = "Hello cả nhà",
                createdAt = ChatTime.nowIso()
            ),
            updatedAt = ChatTime.nowIso()
        )

        assertEquals("Bạn: Hello cả nhà", group.toDomain(currentUserId = "u1").preview)
    }

    @Test
    fun testGroupWithoutNameFallsBackToMemberNames() {
        val me = ParticipantDto("u1", "alice", "Alice Smith")
        val bob = ParticipantDto("u2", "bob", "Bob Jones")
        val carol = ParticipantDto("u3", "carol", "Carol White")

        val group = ConversationDto(
            id = "g3",
            type = "GROUP",
            name = null,
            participants = listOf(me, bob, carol),
            updatedAt = ChatTime.nowIso()
        )

        assertEquals("Bob Jones, Carol White", group.toDomain(currentUserId = "u1").user.name)
    }

    @Test
    fun `shared reel keeps canonical id and has a useful conversation preview`() {
        val dto = MessageDto(
            id = "m-shared",
            conversationId = "c1",
            senderId = "u2",
            type = "REEL",
            textContent = "Highlight trận đấu",
            mediaUrl = "reels/r1/thumbnail",
            sharedContentId = "r1",
            createdAt = ChatTime.nowIso(),
        )

        val message = dto.toDomain(currentUserId = "u1")
        assertEquals("r1", message.sharedContentId)
        assertEquals("reels/r1/thumbnail", message.mediaUrl)

        val conversation = ConversationDto(
            id = "c1",
            participants = listOf(
                ParticipantDto("u1", "alice", "Alice"),
                ParticipantDto("u2", "bob", "Bob"),
            ),
            lastMessage = dto,
            updatedAt = dto.createdAt,
        ).toDomain(currentUserId = "u1")
        assertEquals("Đã chia sẻ một Reel", conversation.preview)
    }
}
