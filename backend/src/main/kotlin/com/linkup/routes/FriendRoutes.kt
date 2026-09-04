package com.linkup.routes

import com.linkup.model.ApiError
import com.linkup.model.UnreadCountDto
import com.linkup.repository.FriendActionException
import com.linkup.repository.FriendRepository
import com.linkup.repository.NotificationRepository
import com.linkup.repository.ProfileRepository
import com.linkup.websocket.ChatWebSocketManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

private const val DEFAULT_LIMIT = 20
private const val MAX_LIMIT = 50
private const val SUGGESTION_LIMIT = 20

/**
 * Friends: requests, responses, lists and suggestions.
 *
 * Friendship is mutual and negotiated, which is what separates it from following:
 * a follow takes effect immediately and one-sidedly, a friendship needs both people.
 */
fun Route.friendRoutes(
    friendRepository: FriendRepository,
    profileRepository: ProfileRepository,
    notificationRepository: NotificationRepository,
    wsManager: ChatWebSocketManager
) {
    val notifyBoth: suspend (UUID, UUID) -> Unit = { me, other ->
        notifySocialRealtime(
            setOf(me, other),
            friendRepository,
            notificationRepository,
            wsManager
        )
    }

    authenticate {
        route("/friends") {

            get {
                val userId = call.currentUserId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                val target = resolveUser(call.parameters["of"], userId, profileRepository) ?: userId
                call.respond(
                    HttpStatusCode.OK,
                    friendRepository.friends(
                        userId = target,
                        viewerId = userId,
                        cursor = call.request.queryParameters["cursor"],
                        limit = limitOf(call.request.queryParameters["limit"])
                    )
                )
            }

            get("/requests/incoming") {
                val userId = call.currentUserId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                call.respond(
                    HttpStatusCode.OK,
                    friendRepository.incomingRequests(
                        userId = userId,
                        cursor = call.request.queryParameters["cursor"],
                        limit = limitOf(call.request.queryParameters["limit"])
                    )
                )
            }

            get("/requests/outgoing") {
                val userId = call.currentUserId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                call.respond(
                    HttpStatusCode.OK,
                    friendRepository.outgoingRequests(
                        userId = userId,
                        cursor = call.request.queryParameters["cursor"],
                        limit = limitOf(call.request.queryParameters["limit"])
                    )
                )
            }

            /** Drives the badge on the Requests tab. */
            get("/requests/count") {
                val userId = call.currentUserId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                call.respond(
                    HttpStatusCode.OK,
                    UnreadCountDto(friendRepository.incomingRequestCount(userId))
                )
            }

            get("/suggestions") {
                val userId = call.currentUserId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                call.respond(
                    HttpStatusCode.OK,
                    friendRepository.suggestions(userId, SUGGESTION_LIMIT)
                )
            }

            get("/{id}/state") {
                friendAction(
                    friendRepository,
                    profileRepository,
                    afterSuccess = { _, _ -> }
                ) { me, other -> friendRepository.state(me, other) }
            }

            post("/{id}/request") {
                friendAction(
                    friendRepository,
                    profileRepository,
                    afterSuccess = notifyBoth
                ) { me, other -> friendRepository.sendRequest(me, other) }
            }

            delete("/{id}/request") {
                friendAction(
                    friendRepository,
                    profileRepository,
                    afterSuccess = notifyBoth
                ) { me, other -> friendRepository.cancelRequest(me, other) }
            }

            put("/{id}/accept") {
                friendAction(
                    friendRepository,
                    profileRepository,
                    afterSuccess = notifyBoth
                ) { me, other -> friendRepository.respond(me, other, accept = true) }
            }

            put("/{id}/decline") {
                friendAction(
                    friendRepository,
                    profileRepository,
                    afterSuccess = notifyBoth
                ) { me, other -> friendRepository.respond(me, other, accept = false) }
            }

            delete("/{id}") {
                friendAction(
                    friendRepository,
                    profileRepository,
                    afterSuccess = notifyBoth
                ) { me, other -> friendRepository.unfriend(me, other) }
            }
        }
    }
}

/**
 * Shared plumbing for the per-person actions: authenticate, resolve the target,
 * run the action, and turn a rule violation into a 409 rather than a 500.
 */
private suspend inline fun RoutingContext.friendAction(
    friendRepository: FriendRepository,
    profileRepository: ProfileRepository,
    crossinline afterSuccess: suspend (me: UUID, other: UUID) -> Unit,
    action: (me: UUID, other: UUID) -> Any
) {
    val me = call.currentUserId()
        ?: return call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
    val other = resolveUser(call.parameters["id"], me, profileRepository)
        ?: return call.respond(HttpStatusCode.NotFound, ApiError("Profile not found"))

    try {
        val result = action(me, other)
        afterSuccess(me, other)
        call.respond(HttpStatusCode.OK, result)
    } catch (e: FriendActionException) {
        call.respond(HttpStatusCode.Conflict, ApiError(e.message))
    }
}

private suspend fun notifySocialRealtime(
    userIds: Set<UUID>,
    friendRepository: FriendRepository,
    notificationRepository: NotificationRepository,
    wsManager: ChatWebSocketManager
) {
    userIds.forEach { userId ->
        runCatching {
            wsManager.notifyNotificationsChanged(
                userId = userId,
                unreadNotifications = notificationRepository.unreadCount(userId),
                pendingFriendRequests = friendRepository.incomingRequestCount(userId)
            )
        }
    }
}

/** Accepts a UUID, a username, or `me`. */
private suspend fun resolveUser(
    raw: String?,
    viewerId: UUID,
    repository: ProfileRepository
): UUID? {
    if (raw.isNullOrBlank()) return null
    if (raw == "me") return viewerId
    runCatching { UUID.fromString(raw) }.getOrNull()?.let { return it }
    return repository.findIdByUsername(raw)
}

private fun limitOf(raw: String?): Int = (raw?.toIntOrNull() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
