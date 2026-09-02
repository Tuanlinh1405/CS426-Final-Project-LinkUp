package com.linkup.routes

import com.linkup.model.ApiError
import com.linkup.model.NotificationBulkResultDto
import com.linkup.model.UnreadCountDto
import com.linkup.repository.NotificationRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

/**
 * Notification endpoints. Every route is scoped to the caller, so a notification
 * belonging to someone else is indistinguishable from one that does not exist.
 */
fun Route.notificationRoutes(notificationRepository: NotificationRepository) {
    authenticate {
        route("/notifications") {

            get {
                val userId = call.currentUserId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))

                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    ?: NotificationRepository.DEFAULT_LIMIT
                val unreadOnly = call.request.queryParameters["filter"]
                    ?.equals("unread", ignoreCase = true) == true

                call.respond(
                    HttpStatusCode.OK,
                    notificationRepository.list(
                        recipientId = userId,
                        cursor = call.request.queryParameters["cursor"],
                        limit = limit,
                        unreadOnly = unreadOnly
                    )
                )
            }

            get("/unread-count") {
                val userId = call.currentUserId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                call.respond(
                    HttpStatusCode.OK,
                    UnreadCountDto(notificationRepository.unreadCount(userId))
                )
            }

            put("/read-all") {
                val userId = call.currentUserId()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                val affected = notificationRepository.markAllRead(userId)
                call.respond(
                    HttpStatusCode.OK,
                    NotificationBulkResultDto(affected, notificationRepository.unreadCount(userId))
                )
            }

            put("/{id}/read") {
                val userId = call.currentUserId()
                    ?: return@put call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@put call.respond(HttpStatusCode.BadRequest, ApiError("Invalid notification id"))

                // `read=false` lets the client undo an accidental tap.
                val read = call.request.queryParameters["read"]?.toBooleanStrictOrNull() ?: true

                if (!notificationRepository.markRead(userId, id, read)) {
                    return@put call.respond(HttpStatusCode.NotFound, ApiError("Notification not found"))
                }
                call.respond(
                    HttpStatusCode.OK,
                    NotificationBulkResultDto(1, notificationRepository.unreadCount(userId))
                )
            }

            delete("/{id}") {
                val userId = call.currentUserId()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Invalid notification id"))

                if (!notificationRepository.delete(userId, id)) {
                    return@delete call.respond(HttpStatusCode.NotFound, ApiError("Notification not found"))
                }
                call.respond(
                    HttpStatusCode.OK,
                    NotificationBulkResultDto(1, notificationRepository.unreadCount(userId))
                )
            }

            delete {
                val userId = call.currentUserId()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                val affected = notificationRepository.clearAll(userId)
                call.respond(HttpStatusCode.OK, NotificationBulkResultDto(affected, 0))
            }
        }
    }
}
