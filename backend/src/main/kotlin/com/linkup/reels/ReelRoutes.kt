package com.linkup.reels

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

fun Route.reelRoutes(repository: ReelRepository, storage: ReelStorageRegistry = ReelStorageRegistry()) {
    val feed = ReelFeed(repository)
    // Reels are public. Media can be shared; modifying actions still require JWT ownership checks.
    route("/reels/{id}/video") {
        get { call.reelGuard { call.video(repository, storage, head = false) } }
        head { call.reelGuard { call.video(repository, storage, head = true) } }
    }
    get("/reels/{id}/thumbnail") {
        call.reelGuard {
            val asset = repository.asset(ReelRepository.uuid(call.parameters["id"])) ?: throw ReelFailure(404, "Thumbnail not found.")
            val key = asset.thumbnailKey ?: throw ReelFailure(404, "Thumbnail not found.")
            val bytes = withContext(Dispatchers.IO) { storage.get(asset.storageBackend).open(key).use { it.readNBytes(1048577) } }
            if (bytes.size > 1048576) throw ReelFailure(500, "Invalid stored thumbnail.")
            call.respondBytes(bytes, ContentType.Image.JPEG)
        }
    }
    authenticate {
        route("/reels") {
            get {
                call.reelGuard {
                    val user = call.reelUser(repository)
                    val author = call.request.queryParameters["authorId"]?.let(ReelRepository::uuid)
                    call.respond(feed.page(user, author, call.request.queryParameters["cursor"], call.pageLimit()))
                }
            }
            post {
                call.reelGuard {
                    val user = call.reelUser(repository)
                    val temporary = withContext(Dispatchers.IO) { Files.createTempDirectory("linkup-reel-") }
                    try {
                        var caption = ""
                        var identifier: UUID? = null
                        var video: Path? = null
                        var thumbnail: Path? = null
                        val fields = mutableSetOf<String>()
                        val multipart = call.receiveMultipart(formFieldLimit = ReelMedia.MAX_VIDEO_BYTES + 1048576)
                        multipart.forEachPart { part ->
                            try {
                                val name = part.name ?: throw ReelFailure(400, "Unknown upload field.")
                                if (!fields.add(name) || fields.size > 4) throw ReelFailure(400, "Duplicate upload field.")
                                when (part) {
                                    is PartData.FormItem -> when (name) {
                                        "id" -> identifier = ReelRepository.uuid(part.value)
                                        "caption" -> { caption = part.value.trim(); if (caption.length > 2200) throw ReelFailure(400, "Caption is limited to 2200 characters.") }
                                        else -> throw ReelFailure(400, "Unknown upload field.")
                                    }
                                    is PartData.FileItem -> {
                                        if (name !in setOf("video", "thumbnail")) throw ReelFailure(400, "Unknown media field.")
                                        val file = temporary.resolve(if (name == "video") "video.mp4" else "thumbnail.jpg")
                                        withContext(Dispatchers.IO) {
                                            part.provider().toInputStream().use { input -> Files.newOutputStream(file).use { output ->
                                                ReelMedia.copyLimited(input, output, if (name == "video") ReelMedia.MAX_VIDEO_BYTES else 1048576)
                                            } }
                                        }
                                        if (name == "video") video = file else thumbnail = file
                                    }
                                    else -> throw ReelFailure(400, "Invalid upload part.")
                                }
                            } finally { part.dispose() }
                        }
                        val id = identifier ?: throw ReelFailure(400, "Upload identifier is required.")
                        val file = video ?: throw ReelFailure(400, "Select a video.")
                        val existing = repository.get(id, user)
                        if (existing != null) {
                            if (existing.author.id != user.toString()) throw ReelFailure(409, "Upload identifier already used.")
                            call.respond(existing)
                        } else {
                            val metadata = withContext(Dispatchers.IO) { ReelMedia.inspect(file).also { thumbnail?.let(ReelMedia::validateThumbnail) } }
                            val target = storage.current()
                            val prefix = "reels/$user/$id/${UUID.randomUUID()}"
                            val asset = ReelAsset("$prefix.mp4", thumbnail?.let { "$prefix.jpg" }, target.type, Files.size(file))
                            var committed = false
                            try {
                                withContext(Dispatchers.IO) {
                                    target.put(asset.videoKey, file, "video/mp4")
                                    thumbnail?.let { target.put(asset.thumbnailKey!!, it, "image/jpeg") }
                                }
                                // If the client cancels during commit, retain files for the committed row.
                                withContext(NonCancellable) { committed = repository.create(id, user, caption, metadata, asset) }
                                call.respond(HttpStatusCode.Created, repository.get(id, user) ?: throw ReelFailure(500, "Cannot load uploaded reel."))
                            } finally {
                                if (!committed) withContext(NonCancellable + Dispatchers.IO) {
                                    runCatching { target.delete(asset.videoKey) }
                                    asset.thumbnailKey?.let { runCatching { target.delete(it) } }
                                }
                            }
                        }
                    } finally {
                        withContext(NonCancellable + Dispatchers.IO) {
                            Files.deleteIfExists(temporary.resolve("video.mp4")); Files.deleteIfExists(temporary.resolve("thumbnail.jpg")); Files.deleteIfExists(temporary)
                        }
                    }
                }
            }
            route("/{id}") {
                get { call.reelGuard { val user = call.reelUser(repository); call.respond(repository.get(ReelRepository.uuid(call.parameters["id"]), user) ?: throw ReelFailure(404, "Reel not found.")) } }
                delete {
                    call.reelGuard {
                        val asset = repository.delete(ReelRepository.uuid(call.parameters["id"]), call.reelUser(repository))
                        asset?.let { withContext(Dispatchers.IO) {
                            // DB deletion is authoritative. A storage outage may leave an unreachable orphan for cleanup.
                            runCatching { storage.get(it.storageBackend).delete(it.videoKey) }
                            it.thumbnailKey?.let { key -> runCatching { storage.get(it.storageBackend).delete(key) } }
                        } }
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
                put("/reaction") { call.reelGuard { val user = call.reelUser(repository); val id = ReelRepository.uuid(call.parameters["id"]); repository.like(id, user, true); call.respond(repository.get(id, user)!!) } }
                delete("/reaction") { call.reelGuard { val user = call.reelUser(repository); val id = ReelRepository.uuid(call.parameters["id"]); repository.like(id, user, false); call.respond(repository.get(id, user)!!) } }
                put("/hidden") { call.reelGuard { repository.hide(ReelRepository.uuid(call.parameters["id"]), call.reelUser(repository), true); call.respond(HttpStatusCode.NoContent) } }
                delete("/hidden") { call.reelGuard { repository.hide(ReelRepository.uuid(call.parameters["id"]), call.reelUser(repository), false); call.respond(HttpStatusCode.NoContent) } }
                get("/comments") { call.reelGuard { call.reelUser(repository); call.respond(repository.comments(ReelRepository.uuid(call.parameters["id"]), call.request.queryParameters["cursor"], call.pageLimit())) } }
                post("/comments") { call.reelGuard { val user = call.reelUser(repository); call.respond(HttpStatusCode.Created, repository.comment(ReelRepository.uuid(call.parameters["id"]), user, call.receive<AddComment>())) } }
                delete("/comments/{commentId}") { call.reelGuard { repository.deleteComment(ReelRepository.uuid(call.parameters["id"]), ReelRepository.uuid(call.parameters["commentId"]), call.reelUser(repository)); call.respond(HttpStatusCode.NoContent) } }
                post("/events") { call.reelGuard { val user = call.reelUser(repository); repository.watch(ReelRepository.uuid(call.parameters["id"]), user, call.receive<WatchEvent>()); call.respond(HttpStatusCode.NoContent) } }
            }
        }
    }
}

private suspend fun ApplicationCall.reelUser(repository: ReelRepository): UUID {
    val claim = principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString() ?: throw ReelFailure(401, "Please sign in again.")
    val id = ReelRepository.uuid(claim)
    if (!repository.userExists(id)) throw ReelFailure(401, "Account is no longer available.")
    return id
}
private fun ApplicationCall.pageLimit(): Int = request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 30) ?: 15
private suspend fun ApplicationCall.reelGuard(block: suspend () -> Unit) {
    try { block() }
    catch (error: CancellationException) { throw error }
    catch (error: ReelFailure) { respond(HttpStatusCode.fromValue(error.status), ApiError(error.message)) }
    catch (error: io.ktor.server.plugins.BadRequestException) { respond(HttpStatusCode.BadRequest, ApiError("Invalid request.")) }
    catch (error: SQLException) {
        respond(HttpStatusCode.ServiceUnavailable, ApiError(if (error.sqlState == "42P01") "Reels database is not ready. Ask the database owner to apply the Reels migration." else "Reels database is temporarily unavailable."))
    }
    catch (_: Exception) { respond(HttpStatusCode.ServiceUnavailable, ApiError("Reels service or media storage is unavailable. Please retry.")) }
}

private suspend fun ApplicationCall.video(repository: ReelRepository, stores: ReelStorageRegistry, head: Boolean) {
    val asset = repository.asset(ReelRepository.uuid(parameters["id"])) ?: throw ReelFailure(404, "Video not found.")
    val store = stores.get(asset.storageBackend)
    if (!head) {
        store.playbackUrl(asset.videoKey)?.let { url ->
            // 307 preserves Media3's Range header while moving the byte stream off this server.
            response.header(HttpHeaders.Location, url)
            respond(HttpStatusCode.TemporaryRedirect)
            return
        }
    }
    val range = try { ByteRange.parse(request.headers[HttpHeaders.Range], asset.fileSize) } catch (error: ReelFailure) {
        response.header(HttpHeaders.ContentRange, "bytes */${asset.fileSize}"); throw error
    }
    val length = range?.length ?: asset.fileSize
    response.header(HttpHeaders.AcceptRanges, "bytes")
    response.header(HttpHeaders.ContentLength, length.toString())
    range?.let { response.header(HttpHeaders.ContentRange, "bytes ${it.start}-${it.end}/${asset.fileSize}") }
    val responseStatus = if (range == null) HttpStatusCode.OK else HttpStatusCode.PartialContent
    if (head) {
        respond(object : OutgoingContent.NoContent() {
            override val status = responseStatus
            override val contentType = ContentType.Video.MP4
            override val contentLength = length
        })
        return
    }
    val input = withContext(Dispatchers.IO) { store.open(asset.videoKey, range?.start ?: 0, length) }
    respondOutputStream(ContentType.Video.MP4, responseStatus) {
        withContext(Dispatchers.IO) { input.use { stream ->
            val buffer = ByteArray(64 * 1024); var remaining = length
            while (remaining > 0) {
                val count = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count < 0) break
                write(buffer, 0, count); remaining -= count
            }
        } }
    }
}
