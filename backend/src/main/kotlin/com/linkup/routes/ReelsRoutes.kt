package com.linkup.routes

import com.linkup.repository.ReelsRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.reelsRoutes(reelsRepository: ReelsRepository) {
    route("/reels") {
        get {
            try {
                val reels = reelsRepository.getAllReels()
                call.respond(HttpStatusCode.OK, reels)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch reels")))
            }
        }
    }
}
