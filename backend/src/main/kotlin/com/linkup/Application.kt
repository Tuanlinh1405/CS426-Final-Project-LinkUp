package com.linkup

import com.linkup.database.DatabaseFactory
import com.linkup.config.EnvConfig
import com.linkup.repository.ChatRepository
import com.linkup.repository.UserRepository
import com.linkup.routes.authRoutes
import com.linkup.routes.chatRoutes
import com.linkup.service.JwtService
import com.linkup.websocket.ChatWebSocketManager
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

fun main() {
    embeddedServer(Netty, port = EnvConfig.PORT, host = EnvConfig.HOST, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
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
    val chatRepository = ChatRepository()
    val wsManager = ChatWebSocketManager(chatRepository)

    routing {
        get("/") {
            call.respondText("LinkUp Backend is running!")
        }
        authRoutes(userRepository)
        chatRoutes(chatRepository, wsManager, userRepository)
    }
}
