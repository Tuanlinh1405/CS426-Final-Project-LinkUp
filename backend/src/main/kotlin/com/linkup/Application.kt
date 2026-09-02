package com.linkup

import com.linkup.database.DatabaseFactory
import com.linkup.config.EnvConfig
import com.linkup.repository.FriendRepository
import com.linkup.repository.NotificationRepository
import com.linkup.repository.ProfileRepository
import com.linkup.repository.UserRepository
import com.linkup.routes.authRoutes
import com.linkup.routes.friendRoutes
import com.linkup.routes.notificationRoutes
import com.linkup.routes.profileRoutes
import com.linkup.routes.userRoutes
import com.linkup.service.JwtService
import com.linkup.storage.LocalMediaStorage
import com.linkup.storage.MediaStorage
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import java.io.File

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
    val profileRepository = ProfileRepository()
    val notificationRepository = NotificationRepository()
    val friendRepository = FriendRepository()
    val mediaStorage: MediaStorage = LocalMediaStorage()

    routing {
        get("/") {
            call.respondText("LinkUp Backend is running!")
        }

        // Uploaded avatars and covers. Swapping LocalMediaStorage for a MinIO
        // adapter makes this route redundant, not wrong.
        staticFiles("/media", File(EnvConfig.MEDIA_ROOT))

        authRoutes(userRepository)
        profileRoutes(profileRepository, mediaStorage)
        notificationRoutes(notificationRepository)
        userRoutes(profileRepository)
        friendRoutes(friendRepository, profileRepository)
    }
}
