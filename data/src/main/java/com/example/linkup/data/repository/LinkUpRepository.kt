package com.example.linkup.data.repository

import com.example.linkup.data.model.ChatMessage
import com.example.linkup.data.model.Conversation
import com.example.linkup.data.model.NotificationItem
import com.example.linkup.data.model.Post
import com.example.linkup.data.model.Reel
import com.example.linkup.data.model.User

interface LinkUpRepository {
    fun currentUser(): User
    fun feed(): List<Post>
    fun reels(): List<Reel>
    fun conversations(): List<Conversation>
    fun messages(): List<ChatMessage>
    fun notifications(): List<NotificationItem>
    fun createPost(content: String): Post
    fun toggleLike(postId: String): List<Post>
    fun sendMessage(text: String): List<ChatMessage>
}

/** In-memory data source used by previews, UI tests and the runnable prototype. */
class FakeLinkUpRepository : LinkUpRepository {
    private val me = User("u1", "Sarah Jones", "@sarah.j", "SJ", "Product Designer • Coffee lover • Traveler")
    private val alex = User("u2", "Alex Chen", "@alex", "AC")
    private val maria = User("u3", "Maria Garcia", "@maria", "MG")

    private var posts = listOf(
        Post("p1", me, "2h", "Spent the morning exploring this new design system. Small details make a big difference!", "Workspace photo", 124, 18),
        Post("p2", alex, "4h", "Just shipped our latest feature. Proud of this team and excited for what comes next.", "Product preview", 89, 12)
    )
    private var reelsList = emptyList<Reel>()
    private var chatMessages = listOf(
        ChatMessage("m1", "Hey! Did you get a chance to look at the new design?", false, "09:41"),
        ChatMessage("m2", "Yes — the purple direction looks great. I left two notes.", true, "09:43"),
        ChatMessage("m3", "Perfect, I’ll update it today.", false, "09:45")
    )

    override fun currentUser() = me
    override fun feed() = posts
    override fun reels() = reelsList
    override fun conversations() = listOf(
        Conversation("c1", alex, "Perfect, I’ll update it today.", "09:45", 2),
        Conversation("c2", maria, "That photo looks amazing!", "Yesterday"),
        Conversation("c3", User("u4", "Marcus Wong", "@marcus", "MW"), "See you at the meetup", "Mon", 1),
        Conversation("c4", User("u5", "Design Team", "team", "DT"), "Sarah shared a file", "Sun")
    )
    override fun messages() = chatMessages
    override fun notifications() = listOf(
        NotificationItem("n1", alex, "liked your post", "5m"),
        NotificationItem("n2", maria, "started following you", "1h"),
        NotificationItem("n3", User("u4", "Marcus Wong", "@marcus", "MW"), "commented: Love this!", "3h", false)
    )

    override fun createPost(content: String): Post {
        val post = Post("p${posts.size + 1}", me, "Now", content, likes = 0, comments = 0)
        posts = listOf(post) + posts
        return post
    }

    override fun toggleLike(postId: String): List<Post> {
        posts = posts.map { post ->
            if (post.id != postId) post else post.copy(
                liked = !post.liked,
                likes = post.likes + if (post.liked) -1 else 1
            )
        }
        return posts
    }

    override fun sendMessage(text: String): List<ChatMessage> {
        if (text.isNotBlank()) {
            chatMessages = chatMessages + ChatMessage("m${chatMessages.size + 1}", text.trim(), true, "Now")
        }
        return chatMessages
    }
}

