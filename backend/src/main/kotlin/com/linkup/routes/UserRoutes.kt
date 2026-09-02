package com.linkup.routes

import com.linkup.model.ApiError
import com.linkup.repository.ProfileRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

private const val DEFAULT_LIMIT = 20
private const val MAX_LIMIT = 50

/** People discovery: searching for users to follow. */
fun Route.userRoutes(profileRepository: ProfileRepository) {
    authenticate {
        route("/users") {
            get("/search") {
                val viewerId = call.currentUserId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))

                val query = call.request.queryParameters["q"].orEmpty()
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT)
                    .coerceIn(1, MAX_LIMIT)

                call.respond(
                    HttpStatusCode.OK,
                    profileRepository.searchUsers(
                        query = query,
                        viewerId = viewerId,
                        cursor = call.request.queryParameters["cursor"],
                        limit = limit
                    )
                )
            }
        }
    }
}
