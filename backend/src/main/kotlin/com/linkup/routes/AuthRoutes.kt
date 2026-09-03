package com.linkup.routes

import com.linkup.model.AuthResponse
import com.linkup.model.LoginRequest
import com.linkup.model.UserRegistrationRequest
import com.linkup.model.UserResponse
import com.linkup.repository.UserRepository
import com.linkup.service.JwtService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(userRepository: UserRepository) {
    route("/auth") {
        post("/register") {
            val request = try {
                call.receive<UserRegistrationRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                return@post
            }

            try {
                val user = userRepository.registerUser(request)
                if (user != null) {
                    val token = JwtService.generateToken(user.id.value.toString())
                    call.respond(HttpStatusCode.Created, AuthResponse(
                        user = UserResponse(
                            id = user.id.value.toString(),
                            email = user.email,
                            username = user.username,
                            fullName = user.fullName,
                            createdAt = user.createdAt.toString()
                        ),
                        token = token
                    ))
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Could not create user")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Conflict, "Email or Username already exists")
            }
        }

        post("/login") {
            val request = try {
                call.receive<LoginRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                return@post
            }

            val user = userRepository.validateUser(request.emailOrUsername, request.password)
            if (user != null) {
                val token = JwtService.generateToken(user.id.toString())
                call.respond(HttpStatusCode.OK, AuthResponse(
                    user = UserResponse(
                        id = user.id.toString(),
                        email = user.email,
                        username = user.username,
                        fullName = user.fullName,
                        createdAt = user.createdAt
                    ),
                    token = token
                ))
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid email/username or password")
            }
        }
    }
}
