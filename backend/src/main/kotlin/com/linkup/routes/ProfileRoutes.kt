package com.linkup.routes

import com.linkup.model.ApiError
import com.linkup.model.MediaUploadResponse
import com.linkup.model.UpdateProfileRequest
import com.linkup.repository.ProfileConflictException
import com.linkup.repository.ProfileRepository
import com.linkup.service.ProfileValidationException
import com.linkup.storage.LocalMediaStorage
import com.linkup.storage.MediaStorage
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import java.util.UUID

private const val AVATAR_FOLDER = "avatars"
private const val COVER_FOLDER = "covers"

/**
 * Profile endpoints.
 *
 * Everything lives behind `authenticate`, because even viewing another profile
 * needs a viewer identity to resolve `isFollowing` and to hide contact details.
 */
fun Route.profileRoutes(
    profileRepository: ProfileRepository,
    mediaStorage: MediaStorage
) {
    authenticate {
        route("/profile") {

            get("/me") {
                val userId = call.currentUserId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                val profile = profileRepository.getProfile(userId, userId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Account no longer exists"))
                call.respond(HttpStatusCode.OK, profile)
            }

            patch("/me") {
                val userId = call.currentUserId()
                    ?: return@patch call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))

                val request = try {
                    call.receive<UpdateProfileRequest>()
                } catch (e: Exception) {
                    return@patch call.respond(HttpStatusCode.BadRequest, ApiError("Malformed request body"))
                }

                try {
                    val updated = profileRepository.updateProfile(userId, request)
                        ?: return@patch call.respond(
                            HttpStatusCode.NotFound,
                            ApiError("Account no longer exists")
                        )
                    call.respond(HttpStatusCode.OK, updated)
                } catch (e: ProfileValidationException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        ApiError("Please check the highlighted field", mapOf(e.error.field to e.error.message))
                    )
                } catch (e: ProfileConflictException) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ApiError(e.error.message, mapOf(e.error.field to e.error.message))
                    )
                }
            }

            post("/me/avatar") { uploadImage(AVATAR_FOLDER, profileRepository, mediaStorage) }
            post("/me/cover") { uploadImage(COVER_FOLDER, profileRepository, mediaStorage) }

            delete("/me/avatar") { removeImage(AVATAR_FOLDER, profileRepository, mediaStorage) }
            delete("/me/cover") { removeImage(COVER_FOLDER, profileRepository, mediaStorage) }

            get("/{id}") {
                val viewerId = call.currentUserId()
                val targetId = resolveTarget(call.parameters["id"], call.currentUserId(), profileRepository)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Profile not found"))
                val profile = profileRepository.getProfile(targetId, viewerId)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Profile not found"))
                call.respond(HttpStatusCode.OK, profile)
            }

            post("/{id}/follow") {
                val viewerId = call.currentUserId()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                val targetId = resolveTarget(call.parameters["id"], call.currentUserId(), profileRepository)
                    ?: return@post call.respond(HttpStatusCode.NotFound, ApiError("Profile not found"))
                if (targetId == viewerId) {
                    return@post call.respond(HttpStatusCode.BadRequest, ApiError("You cannot follow yourself"))
                }
                call.respond(HttpStatusCode.OK, profileRepository.follow(viewerId, targetId))
            }

            get("/{id}/followers") {
                val viewerId = call.currentUserId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                val targetId = resolveTarget(call.parameters["id"], call.currentUserId(), profileRepository)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Profile not found"))
                call.respond(
                    HttpStatusCode.OK,
                    profileRepository.followers(
                        userId = targetId,
                        viewerId = viewerId,
                        cursor = call.request.queryParameters["cursor"],
                        limit = listLimit(call.request.queryParameters["limit"])
                    )
                )
            }

            get("/{id}/following") {
                val viewerId = call.currentUserId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                val targetId = resolveTarget(call.parameters["id"], call.currentUserId(), profileRepository)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Profile not found"))
                call.respond(
                    HttpStatusCode.OK,
                    profileRepository.following(
                        userId = targetId,
                        viewerId = viewerId,
                        cursor = call.request.queryParameters["cursor"],
                        limit = listLimit(call.request.queryParameters["limit"])
                    )
                )
            }

            delete("/{id}/follow") {
                val viewerId = call.currentUserId()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                val targetId = resolveTarget(call.parameters["id"], call.currentUserId(), profileRepository)
                    ?: return@delete call.respond(HttpStatusCode.NotFound, ApiError("Profile not found"))
                call.respond(HttpStatusCode.OK, profileRepository.unfollow(viewerId, targetId))
            }
        }
    }
}

private fun listLimit(raw: String?): Int = (raw?.toIntOrNull() ?: 20).coerceIn(1, 50)

/**
 * Resolves a path segment to a user id.
 *
 * Accepts a UUID, a username (with or without a leading `@`), or the literal `me`,
 * which resolves to [viewerId]. Without the `me` case, `/profile/me/followers` fell
 * through to the `{id}` route and 404'd.
 */
private suspend fun resolveTarget(
    raw: String?,
    viewerId: UUID?,
    repository: ProfileRepository
): UUID? {
    if (raw.isNullOrBlank()) return null
    if (raw == "me") return viewerId
    runCatching { UUID.fromString(raw) }.getOrNull()?.let { return it }
    return repository.findIdByUsername(raw)
}

/**
 * Handles an avatar or cover multipart upload.
 *
 * Rejects oversized or non-image payloads before writing, stores the file, points the
 * profile at it, then deletes the file it replaced so uploads do not accumulate.
 */
private suspend fun RoutingContext.uploadImage(
    folder: String,
    repository: ProfileRepository,
    storage: MediaStorage
) {
    val userId = call.currentUserId()
        ?: return call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))

    // Cheap rejection before buffering anything.
    val declaredLength = call.request.headers["Content-Length"]?.toLongOrNull()
    if (declaredLength != null && declaredLength > MediaStorage.MAX_IMAGE_BYTES * 2) {
        return call.respond(HttpStatusCode.PayloadTooLarge, ApiError("Image must be 8 MB or smaller"))
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
    } catch (e: Exception) {
        return call.respond(HttpStatusCode.BadRequest, ApiError("Could not read the uploaded file"))
    }

    val payload = bytes
        ?: return call.respond(HttpStatusCode.BadRequest, ApiError("No image was attached"))

    if (payload.isEmpty()) {
        return call.respond(HttpStatusCode.BadRequest, ApiError("The uploaded image is empty"))
    }
    if (payload.size > MediaStorage.MAX_IMAGE_BYTES) {
        return call.respond(HttpStatusCode.PayloadTooLarge, ApiError("Image must be 8 MB or smaller"))
    }

    val resolvedType = contentType?.lowercase()
    if (resolvedType == null || resolvedType !in MediaStorage.ALLOWED_IMAGE_TYPES) {
        return call.respond(
            HttpStatusCode.UnsupportedMediaType,
            ApiError("Use a JPEG, PNG, WebP or GIF image")
        )
    }

    val stored = storage.put(payload, resolvedType, folder)
    val previousUrl = if (folder == AVATAR_FOLDER) {
        repository.setAvatarUrl(userId, stored.url)
    } else {
        repository.setCoverUrl(userId, stored.url)
    }

    // Best effort: a failed cleanup must not fail the upload.
    storage.keyFromUrl(previousUrl)?.let { runCatching { storage.delete(it) } }

    call.respond(
        HttpStatusCode.OK,
        MediaUploadResponse(
            url = stored.url,
            key = stored.key,
            size = stored.size,
            contentType = stored.contentType
        )
    )
}

/** Clears the avatar or cover and removes the backing file. */
private suspend fun RoutingContext.removeImage(
    folder: String,
    repository: ProfileRepository,
    storage: MediaStorage
) {
    val userId = call.currentUserId()
        ?: return call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))

    val previousUrl = if (folder == AVATAR_FOLDER) {
        repository.setAvatarUrl(userId, null)
    } else {
        repository.setCoverUrl(userId, null)
    }
    storage.keyFromUrl(previousUrl)?.let { runCatching { storage.delete(it) } }

    val profile = repository.getProfile(userId, userId)
        ?: return call.respond(HttpStatusCode.NotFound, ApiError("Account no longer exists"))
    call.respond(HttpStatusCode.OK, profile)
}
