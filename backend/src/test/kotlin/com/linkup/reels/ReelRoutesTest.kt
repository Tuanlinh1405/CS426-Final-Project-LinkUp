package com.linkup.reels

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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
import org.jcodec.api.awt.AWTSequenceEncoder
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.util.UUID

class ReelRoutesTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var database: ReelTestDatabase
    private val algorithm = Algorithm.HMAC256("local-tests-only-not-an-app-secret")
    @Before fun setup() { database = ReelTestDatabase() }
    @After fun teardown() { if (::database.isInitialized) database.close() }
    private fun token(id: UUID) = JWT.create().withClaim("userId", id.toString()).sign(algorithm)
    private fun ApplicationTestBuilder.installApp() {
        application {
            install(ContentNegotiation) { json() }
            install(Authentication) { jwt { verifier(JWT.require(algorithm).build()); validate { JWTPrincipal(it.payload) } } }
            routing { reelRoutes(database.repository, ReelStorageRegistry(LocalReelStorage(temporary.root.toPath().resolve("media")))) }
        }
    }
    private fun sampleVideo(): ByteArray {
        val file = temporary.newFile("sample.mp4")
        val encoder = AWTSequenceEncoder.createSequenceEncoder(file, 25)
        repeat(25) { frame ->
            val image = BufferedImage(64, 96, BufferedImage.TYPE_3BYTE_BGR)
            val graphics = image.createGraphics()
            graphics.color = Color(60 + frame * 3, 50, 180); graphics.fillRect(0, 0, 64, 96)
            graphics.dispose(); encoder.encodeImage(image)
        }
        encoder.finish()
        assertEquals(1000, ReelMedia.inspect(file.toPath()).durationMs)
        return file.readBytes()
    }

    @Test fun `upload playback range reactions comments and delete work end to end`() = testApplication {
        installApp()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/reels").status)
        val id = UUID.randomUUID().toString()
        val bytes = sampleVideo()
        val cover = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(64, 96, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray()
        suspend fun upload() = client.post("/reels") {
            bearerAuth(token(database.alice))
            setBody(MultiPartFormDataContent(formData {
                append("id", id); append("caption", "A real test reel")
                append("video", bytes, Headers.build { append(HttpHeaders.ContentType, "video/mp4"); append(HttpHeaders.ContentDisposition, "filename=sample.mp4") })
                append("thumbnail", cover, Headers.build { append(HttpHeaders.ContentType, "image/jpeg"); append(HttpHeaders.ContentDisposition, "filename=cover.jpg") })
            }))
        }
        val created = upload()
        assertEquals(created.bodyAsText(), HttpStatusCode.Created, created.status)
        val reel = Json.decodeFromString<ReelDto>(created.bodyAsText())
        assertEquals(id, reel.id); assertEquals(database.alice.toString(), reel.author.id)
        assertEquals(HttpStatusCode.OK, upload().status)
        val thumbnail = client.get("/reels/$id/thumbnail")
        assertEquals(HttpStatusCode.OK, thumbnail.status); assertArrayEquals(cover, thumbnail.body<ByteArray>())
        val video = client.get("/reels/$id/video") { header(HttpHeaders.Range, "bytes=0-7") }
        assertEquals(HttpStatusCode.PartialContent, video.status)
        assertArrayEquals(bytes.take(8).toByteArray(), video.body<ByteArray>())
        assertEquals("bytes 0-7/${bytes.size}", video.headers[HttpHeaders.ContentRange])
        val head = client.head("/reels/$id/video")
        assertEquals(HttpStatusCode.OK, head.status); assertEquals(bytes.size.toString(), head.headers[HttpHeaders.ContentLength])
        assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, client.get("/reels/$id/video") { header(HttpHeaders.Range, "bytes=999999999-") }.status)
        repeat(2) { assertEquals(HttpStatusCode.OK, client.put("/reels/$id/reaction") { bearerAuth(token(database.bob)) }.status) }
        val comment = client.post("/reels/$id/comments") {
            bearerAuth(token(database.bob)); contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(AddComment(UUID.randomUUID().toString(), "Great video")))
        }
        assertEquals(HttpStatusCode.Created, comment.status)
        val detail = Json.decodeFromString<ReelDto>(client.get("/reels/$id") { bearerAuth(token(database.bob)) }.bodyAsText())
        assertTrue(detail.liked); assertEquals(1, detail.likeCount); assertEquals(1, detail.commentCount)
        assertEquals(HttpStatusCode.Forbidden, client.delete("/reels/$id") { bearerAuth(token(database.bob)) }.status)
        assertEquals(HttpStatusCode.NoContent, client.delete("/reels/$id") { bearerAuth(token(database.alice)) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/reels/$id/video").status)
    }

    @Test fun `invalid upload and malformed event are rejected`() = testApplication {
        installApp()
        val invalid = client.post("/reels") {
            bearerAuth(token(database.alice))
            setBody(MultiPartFormDataContent(formData {
                append("id", UUID.randomUUID().toString())
                append("video", ByteArray(30), Headers.build { append(HttpHeaders.ContentDisposition, "filename=fake.mp4") })
            }))
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertTrue(database.repository.candidates(database.alice).isEmpty())
        assertEquals(HttpStatusCode.BadRequest, client.get("/reels/not-a-uuid") { bearerAuth(token(database.alice)) }.status)
    }
}
