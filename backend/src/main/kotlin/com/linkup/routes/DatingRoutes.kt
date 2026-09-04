package com.linkup.routes

import com.linkup.model.DatingProfileRequest
import com.linkup.model.DatingProfileResponse
import com.linkup.model.SwipeRequest
import com.linkup.repository.DatingRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.datingRoutes(repository: DatingRepository) {
    authenticate {
        route("/dating") {
            get("/profile") {
                val userId = call.currentUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(
                    repository.getProfile(userId)
                        ?: DatingProfileResponse(
                            userId = userId.toString(),
                            bio = null,
                            interests = emptyList(),
                            lookingFor = "RELATIONSHIP",
                            preferredGender = null,
                            minAge = null,
                            maxAge = null
                        )
                )
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
