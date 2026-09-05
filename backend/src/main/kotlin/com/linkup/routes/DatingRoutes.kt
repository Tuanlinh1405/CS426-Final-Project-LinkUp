package com.linkup.routes

import com.linkup.model.DatingProfileRequest
import com.linkup.model.DatingProfileResponse
import com.linkup.model.SwipeRequest
import com.linkup.repository.DatingRepository
import com.linkup.storage.MediaStorage
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.util.UUID

private const val DATING_PHOTO_FOLDER = "dating"

fun Route.datingRoutes(repository: DatingRepository, mediaStorage: MediaStorage? = null) {
    authenticate {
        route("/dating") {
            get("/profile") {
                val userId = call.currentUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(repository.identity(userId))
            }

            put("/profile") {
                val userId = call.currentUserId() ?: return@put call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<DatingProfileRequest>()
                call.respond(repository.upsertProfile(userId, request))
            }

            get("/discover") {
                val userId = call.currentUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(repository.discover(userId))
            }

            post("/swipes") {
                val userId = call.currentUserId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<SwipeRequest>()
                try {
                    call.respond(repository.swipe(userId, request))
                } catch (exception: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, exception.message ?: "Invalid swipe")
                }
            }

            post("/swipes/reset-passed") {
                val userId = call.currentUserId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                repository.resetPassedSwipes(userId)
                call.respond(HttpStatusCode.NoContent)
            }

            post("/profile/photos") {
                val userId = call.currentUserId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val storage = mediaStorage
                    ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, "Photo storage is not configured")

                val declaredLength = call.request.headers["Content-Length"]?.toLongOrNull()
                if (declaredLength != null && declaredLength > MediaStorage.MAX_IMAGE_BYTES * 2) {
                    return@post call.respond(HttpStatusCode.PayloadTooLarge, "Image must be 8 MB or smaller")
                }

                var bytes: ByteArray? = null
                var contentType: String? = null
                try {
                    call.receiveMultipart().forEachPart { part ->
                        if (part is PartData.FileItem && bytes == null) {
                            contentType = part.contentType?.let { "${it.contentType}/${it.contentSubtype}" }
                            bytes = part.provider().readRemaining().readByteArray()
                        }
                        part.dispose()
                    }
                } catch (exception: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Could not read the uploaded file")
                }

                val payload = bytes
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "No image was attached")
                if (payload.isEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, "The uploaded image is empty")
                }
                if (payload.size > MediaStorage.MAX_IMAGE_BYTES) {
                    return@post call.respond(HttpStatusCode.PayloadTooLarge, "Image must be 8 MB or smaller")
                }
                val resolvedType = contentType?.lowercase()
                if (resolvedType == null || resolvedType !in MediaStorage.ALLOWED_IMAGE_TYPES) {
                    return@post call.respond(HttpStatusCode.UnsupportedMediaType, "Use a JPEG, PNG, WebP or GIF image")
                }

                val stored = storage.put(payload, resolvedType, DATING_PHOTO_FOLDER)
                try {
                    val photos = repository.addPhoto(userId, stored.url, repository.nextPhotoOrder(userId))
                    call.respond(HttpStatusCode.OK, photos)
                } catch (exception: IllegalStateException) {
                    runCatching { storage.delete(stored.key) }
                    call.respond(HttpStatusCode.Conflict, exception.message ?: "Could not attach that photo")
                }
            }

            delete("/profile/photos/{id}") {
                val userId = call.currentUserId() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
                val photoId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid photo id")
                val removedUrl = repository.photoUrl(userId, photoId)
                val photos = repository.deletePhoto(userId, photoId)
                mediaStorage?.keyFromUrl(removedUrl)?.let { key -> runCatching { mediaStorage.delete(key) } }
                call.respond(HttpStatusCode.OK, photos)
            }

            get("/matches") {
                val userId = call.currentUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(repository.matches(userId))
            }

            get("/notifications") {
                val userId = call.currentUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(repository.notifications(userId))
            }
        }
    }
}
