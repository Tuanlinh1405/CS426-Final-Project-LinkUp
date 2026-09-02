package com.linkup.routes

import com.linkup.model.CreatePostRequest
import com.linkup.repository.PostRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.postRoutes(postRepository: PostRepository) {
    route("/feed") {
        get {
            try {
                val feed = postRepository.getFeed()
                call.respond(HttpStatusCode.OK, feed)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Failed to fetch feed")))
            }
        }
    }
    
    route("/posts") {
        authenticate {
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asString()
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                    return@post
                }
                
                try {
                    val request = call.receive<CreatePostRequest>()
                    val newPost = postRepository.createPost(userId, request.content, request.privacyLevel)
                    if (newPost != null) {
                        call.respond(HttpStatusCode.Created, newPost)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Failed to create post. User may not exist."))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (e.message ?: "Server error")))
                }
            }
        }
    }
}
