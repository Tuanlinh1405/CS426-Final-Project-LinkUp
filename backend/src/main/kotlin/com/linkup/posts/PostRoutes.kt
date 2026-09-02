package com.linkup.posts

import com.linkup.reels.ReelFailure
import com.linkup.reels.ReelStorage
import com.linkup.reels.ReelStorageRegistry
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException
import java.util.UUID

fun Route.postRoutes(repository: PostRepository, storage: ReelStorageRegistry = ReelStorageRegistry()) {
    get("/media/{id}") {
        call.postGuard {
            val asset = repository.media(PostRepository.uuid(call.parameters["id"])) ?: throw PostFailure(404, "Media not found.")
            val store = storage.current()
            store.playbackUrl(asset.storageKey)?.let { url ->
                call.response.header(HttpHeaders.Location, url)
                call.respond(HttpStatusCode.TemporaryRedirect)
                return@postGuard
            }
            val bytes = withContext(Dispatchers.IO) { store.open(asset.storageKey).use { it.readBytes() } }
            call.respondBytes(bytes, runCatching { ContentType.parse(asset.mimeType) }.getOrDefault(ContentType.Application.OctetStream))
        }
    }
    authenticate {
        route("/posts") {
            get {
                call.postGuard {
                    val user = call.postUser(repository)
                    call.respond(repository.page(user, call.request.queryParameters["cursor"], call.postPageLimit()).directMedia(storage.current()))
                }
            }
            post {
                call.postGuard {
                    val user = call.postUser(repository)
                    val temporary = withContext(Dispatchers.IO) { Files.createTempDirectory("linkup-post-") }
                    val files = mutableListOf<UploadedImage>()
                    try {
                        var id: UUID? = null
                        var content = ""
                        val scalarFields = mutableSetOf<String>()
                        call.receiveMultipart(formFieldLimit = MAX_POST_UPLOAD_BYTES + 16_384).forEachPart { part ->
                            try {
                                when (part) {
                                    is PartData.FormItem -> {
                                        val name = part.name ?: throw PostFailure(400, "Unknown post field.")
                                        if (name !in setOf("id", "content") || !scalarFields.add(name)) throw PostFailure(400, "Invalid or duplicate post field.")
                                        if (name == "id") id = PostRepository.uuid(part.value) else content = part.value
                                    }
                                    is PartData.FileItem -> {
                                        if (part.name != "media" || files.size >= MAX_POST_IMAGES) throw PostFailure(400, "A post can contain up to 4 photos.")
                                        val mime = part.contentType?.toString()?.lowercase() ?: ""
                                        val extension = when (mime) {
                                            "image/jpeg" -> "jpg"
                                            "image/png" -> "png"
                                            "image/webp" -> "webp"
                                            else -> throw PostFailure(400, "Only JPEG, PNG or WebP photos are supported.")
                                        }
                                        val file = temporary.resolve("image-${files.size}.$extension")
                                        val size = withContext(Dispatchers.IO) {
                                            part.provider().toInputStream().use { input -> Files.newOutputStream(file).use { output -> copyImage(input, output) } }
                                        }
                                        files += UploadedImage(file, mime, extension, size)
                                    }
                                    else -> throw PostFailure(400, "Invalid post upload part.")
                                }
                            } finally { part.dispose() }
                        }
                        val postId = id ?: throw PostFailure(400, "Post identifier is required.")
                        repository.get(postId, user)?.let { existing ->
                            if (existing.author.id != user.toString()) throw PostFailure(409, "Post identifier already used.")
                            call.respond(existing.directMedia(storage.current()))
                            return@postGuard
                        }
                        val store = storage.current()
                        val assets = files.map { image ->
                            val mediaId = UUID.randomUUID()
                            NewPostMedia(mediaId, "posts/$user/$postId/$mediaId.${image.extension}", image.mimeType, image.size)
                        }
                        val uploaded = mutableListOf<String>()
                        var committed = false
                        try {
                            withContext(Dispatchers.IO) {
                                files.zip(assets).forEach { (image, asset) ->
                                    store.put(asset.storageKey, image.path, image.mimeType)
                                    uploaded += asset.storageKey
                                }
                            }
                            withContext(NonCancellable) { committed = repository.create(postId, user, content, assets) }
                            val result = repository.get(postId, user) ?: throw PostFailure(500, "Cannot load published post.")
                            call.respond(HttpStatusCode.Created, result.directMedia(store))
                        } finally {
                            if (!committed) withContext(NonCancellable + Dispatchers.IO) { uploaded.forEach { runCatching { store.delete(it) } } }
                        }
                    } finally {
                        withContext(NonCancellable + Dispatchers.IO) {
                            files.forEach { Files.deleteIfExists(it.path) }
                            Files.deleteIfExists(temporary)
                        }
                    }
                }
            }
            route("/{id}") {
                get { call.postGuard { val user = call.postUser(repository); call.respond((repository.get(PostRepository.uuid(call.parameters["id"]), user) ?: throw PostFailure(404, "Post not found.")).directMedia(storage.current())) } }
                delete {
                    call.postGuard {
                        val assets = repository.delete(PostRepository.uuid(call.parameters["id"]), call.postUser(repository))
                        withContext(Dispatchers.IO) { assets.forEach { runCatching { storage.current().delete(it.storageKey) } } }
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
                put("/reaction") { call.postGuard { val user = call.postUser(repository); call.respond(repository.like(PostRepository.uuid(call.parameters["id"]), user, true).directMedia(storage.current())) } }
                delete("/reaction") { call.postGuard { val user = call.postUser(repository); call.respond(repository.like(PostRepository.uuid(call.parameters["id"]), user, false).directMedia(storage.current())) } }
                get("/comments") { call.postGuard { val user = call.postUser(repository); call.respond(repository.comments(PostRepository.uuid(call.parameters["id"]), user, call.request.queryParameters["cursor"], call.postPageLimit())) } }
                post("/comments") { call.postGuard { val user = call.postUser(repository); call.respond(HttpStatusCode.Created, repository.comment(PostRepository.uuid(call.parameters["id"]), user, call.receive<AddPostComment>())) } }
                delete("/comments/{commentId}") { call.postGuard { repository.deleteComment(PostRepository.uuid(call.parameters["id"]), PostRepository.uuid(call.parameters["commentId"]), call.postUser(repository)); call.respond(HttpStatusCode.NoContent) } }
            }
        }
    }
}

private fun PostPage.directMedia(store: ReelStorage) = copy(items = items.map { it.directMedia(store) })
private fun PostDto.directMedia(store: ReelStorage) = copy(media = media.map { item ->
    item.copy(url = item.storageKey?.let(store::playbackUrl) ?: item.url)
})

private data class UploadedImage(val path: Path, val mimeType: String, val extension: String, val size: Long)
private const val MAX_POST_IMAGES = 4
private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
private const val MAX_POST_UPLOAD_BYTES = MAX_POST_IMAGES * MAX_IMAGE_BYTES
private fun copyImage(input: java.io.InputStream, output: java.io.OutputStream): Long {
    val buffer = ByteArray(64 * 1024); var total = 0L
    while (true) {
        val count = input.read(buffer); if (count < 0) break
        total += count
        if (total > MAX_IMAGE_BYTES) throw PostFailure(413, "Each photo must be 10 MB or smaller.")
        output.write(buffer, 0, count)
    }
    if (total == 0L) throw PostFailure(400, "Selected photo is empty.")
    return total
}
private suspend fun ApplicationCall.postUser(repository: PostRepository): UUID {
    val claim = principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString() ?: throw PostFailure(401, "Please sign in again.")
    val id = PostRepository.uuid(claim)
    if (!repository.userExists(id)) throw PostFailure(401, "Account is no longer available.")
    return id
}
private fun ApplicationCall.postPageLimit() = request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 30) ?: 15
private suspend fun ApplicationCall.postGuard(block: suspend () -> Unit) {
    try { block() }
    catch (error: CancellationException) { throw error }
    catch (error: PostFailure) { respond(HttpStatusCode.fromValue(error.status), PostApiError(error.message)) }
    catch (error: ReelFailure) { respond(HttpStatusCode.fromValue(error.status), PostApiError(error.message)) }
    catch (error: io.ktor.server.plugins.BadRequestException) { respond(HttpStatusCode.BadRequest, PostApiError("Invalid request.")) }
    catch (_: SQLException) { respond(HttpStatusCode.ServiceUnavailable, PostApiError("Feed database is temporarily unavailable.")) }
    catch (_: Exception) { respond(HttpStatusCode.ServiceUnavailable, PostApiError("Feed or media storage is unavailable. Please retry.")) }
}
