package com.linkup.posts

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.linkup.reels.LocalReelStorage
import com.linkup.reels.ReelStorageRegistry
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

class PostRoutesTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var database: PostTestDatabase
    private val algorithm = Algorithm.HMAC256("local-post-tests-only")
    @Before fun setup() { database = PostTestDatabase() }
    @After fun teardown() { if (::database.isInitialized) database.close() }
    private fun token(id: UUID) = JWT.create().withClaim("userId", id.toString()).sign(algorithm)
    private fun ApplicationTestBuilder.installApp() {
        application {
            install(ContentNegotiation) { json() }
            install(Authentication) { jwt { verifier(JWT.require(algorithm).build()); validate { JWTPrincipal(it.payload) } } }
            routing { postRoutes(database.repository, ReelStorageRegistry(LocalReelStorage(temporary.root.toPath().resolve("media")))) }
        }
    }
    private fun jpeg(): ByteArray = ByteArrayOutputStream().also { output ->
        val image = BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().also { graphics -> graphics.color = Color(120, 60, 210); graphics.fillRect(0, 0, 80, 60); graphics.dispose() }
        ImageIO.write(image, "jpg", output)
    }.toByteArray()

    @Test fun `photo post feed media like comment and delete work end to end`() = testApplication {
        installApp()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/posts").status)
        val postId = UUID.randomUUID().toString()
        val photo = jpeg()
        val created = client.post("/posts") {
            bearerAuth(token(database.alice))
            setBody(MultiPartFormDataContent(formData {
                append("id", postId); append("content", "A database-backed photo post")
                append("media", photo, Headers.build { append(HttpHeaders.ContentType, "image/jpeg"); append(HttpHeaders.ContentDisposition, "filename=photo.jpg") })
            }))
        }
        assertEquals(created.bodyAsText(), HttpStatusCode.Created, created.status)
        val post = Json.decodeFromString<PostDto>(created.bodyAsText())
        assertEquals(database.alice.toString(), post.author.id); assertEquals(1, post.media.size)
        val feed = Json.decodeFromString<PostPage>(client.get("/posts") { bearerAuth(token(database.bob)) }.bodyAsText())
        assertEquals(postId, feed.items.single().id)
        assertArrayEquals(photo, client.get("/${post.media.single().url}").body<ByteArray>())
        repeat(2) { assertEquals(HttpStatusCode.OK, client.put("/posts/$postId/reaction") { bearerAuth(token(database.bob)) }.status) }
        val commentId = UUID.randomUUID().toString()
        val comment = client.post("/posts/$postId/comments") {
            bearerAuth(token(database.bob)); contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(AddPostComment(commentId, "Looks great")))
        }
        assertEquals(HttpStatusCode.Created, comment.status)
        val rootComment = Json.decodeFromString<PostCommentDto>(comment.bodyAsText())
        val reply = client.post("/posts/$postId/comments") {
            bearerAuth(token(database.alice)); contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(AddPostComment(UUID.randomUUID().toString(), "Thank you", rootComment.id)))
        }
        assertEquals(HttpStatusCode.Created, reply.status)
        val comments = Json.decodeFromString<PostCommentPage>(client.get("/posts/$postId/comments") { bearerAuth(token(database.bob)) }.bodyAsText())
        assertEquals("Thank you", comments.items.single().replies.single().content)
        assertEquals(rootComment.id, comments.items.single().replies.single().parentId)
        repeat(2) {
            val likedComment = client.put("/posts/$postId/comments/${rootComment.id}/reaction") { bearerAuth(token(database.alice)) }
            assertEquals(HttpStatusCode.OK, likedComment.status)
            assertEquals(1, Json.decodeFromString<PostCommentDto>(likedComment.bodyAsText()).likeCount)
        }
        val unlikedComment = client.delete("/posts/$postId/comments/${rootComment.id}/reaction") { bearerAuth(token(database.alice)) }
        assertEquals(HttpStatusCode.OK, unlikedComment.status)
        assertFalse(Json.decodeFromString<PostCommentDto>(unlikedComment.bodyAsText()).liked)
        val detail = Json.decodeFromString<PostDto>(client.get("/posts/$postId") { bearerAuth(token(database.bob)) }.bodyAsText())
        assertTrue(detail.liked); assertEquals(1, detail.likeCount); assertEquals(2, detail.commentCount)
        database.connect().use { db ->
            db.createStatement().use { statement -> statement.executeQuery("SELECT COUNT(*) FROM notifications").use { result ->
                result.next(); assertEquals(3, result.getInt(1))
            } }
        }
        assertEquals(HttpStatusCode.Forbidden, client.delete("/posts/$postId") { bearerAuth(token(database.bob)) }.status)
        assertEquals(HttpStatusCode.NoContent, client.delete("/posts/$postId") { bearerAuth(token(database.alice)) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/media/${post.media.single().id}").status)
    }
}
