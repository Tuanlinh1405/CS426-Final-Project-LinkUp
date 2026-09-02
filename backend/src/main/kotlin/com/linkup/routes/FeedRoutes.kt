package com.linkup.routes

import com.linkup.repository.FeedRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.feedRoutes(feedRepository: FeedRepository) {
    route("/feed") {
        get {
            try {
                val feed = feedRepository.getFeed()
                call.respond(HttpStatusCode.OK, feed)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch feed")))
            }
        }
    }
}
