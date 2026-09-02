package com.linkup

import com.linkup.config.EnvConfig
import com.linkup.database.DatabaseFactory
import com.linkup.repository.ReelsRepository
import com.linkup.repository.UserRepository
import com.linkup.routes.authRoutes
import com.linkup.routes.reelsRoutes
import com.linkup.service.JwtService
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = EnvConfig.PORT, host = EnvConfig.HOST, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    
    install(Authentication) {
        jwt {
            verifier(JwtService.getVerifier())
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
    
    // Initialize Database
    DatabaseFactory.init()

    val userRepository = UserRepository()
    val reelsRepository = ReelsRepository()

    routing {
        get("/") {
            call.respondText("LinkUp Backend is running!")
        }
        route("/api/v1") {
            authRoutes(userRepository)
            reelsRoutes(reelsRepository)
        }
        authRoutes(userRepository)
        reelsRoutes(reelsRepository)
    }
}
