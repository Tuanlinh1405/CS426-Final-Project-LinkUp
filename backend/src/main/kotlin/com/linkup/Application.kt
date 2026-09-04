package com.linkup

import com.linkup.ai.AiRepository
import com.linkup.ai.AiAnalysisService
import com.linkup.ai.GeminiClient
import com.linkup.ai.aiRoutes
import com.linkup.database.DatabaseFactory
import com.linkup.config.EnvConfig
import com.linkup.repository.ChatRepository
import com.linkup.repository.FriendRepository
import com.linkup.repository.NotificationRepository
import com.linkup.repository.ProfileRepository
import com.linkup.repository.UserRepository
import com.linkup.routes.authRoutes
import com.linkup.routes.datingRoutes
import com.linkup.routes.chatRoutes
import com.linkup.routes.friendRoutes
import com.linkup.routes.notificationRoutes
import com.linkup.routes.profileRoutes
import com.linkup.routes.userRoutes
import com.linkup.service.JwtService
import com.linkup.storage.LocalMediaStorage
import com.linkup.storage.MediaStorage
import com.linkup.websocket.ChatWebSocketManager
import com.linkup.posts.PostRepository
import com.linkup.posts.postRoutes
import com.linkup.reels.ReelRepository
import com.linkup.reels.reelRoutes
import com.linkup.search.SearchRepository
import com.linkup.search.searchRoutes
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
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds
import java.io.File

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
    val datingRepository = com.linkup.repository.DatingRepository()
    val chatRepository = ChatRepository()
    val profileRepository = ProfileRepository()
    val notificationRepository = NotificationRepository()
    val friendRepository = FriendRepository()
    val wsManager = ChatWebSocketManager(chatRepository)
    val mediaStorage: MediaStorage = LocalMediaStorage()
    val reelRepository = ReelRepository()
    val postRepository = PostRepository()
    val searchRepository = SearchRepository()
    val aiRepository = AiRepository()
    val geminiClient = GeminiClient()
    val aiAnalysisService = AiAnalysisService(aiRepository, geminiClient)
    monitor.subscribe(ApplicationStopped) { aiAnalysisService.close() }

    routing {
        get("/") {
            call.respondText("LinkUp Backend is running!")
        }

        // Uploaded avatars and covers. Swapping LocalMediaStorage for a MinIO
        // adapter makes this route redundant, not wrong.
        staticFiles("/media", File(EnvConfig.MEDIA_ROOT))

        authRoutes(userRepository)
        datingRoutes(datingRepository)
        chatRoutes(chatRepository, wsManager, userRepository, mediaStorage)
        profileRoutes(profileRepository, mediaStorage)
        notificationRoutes(notificationRepository, wsManager)
        userRoutes(profileRepository)
        friendRoutes(friendRepository, profileRepository, notificationRepository, wsManager)
        reelRoutes(reelRepository)
        postRoutes(postRepository)
        searchRoutes(searchRepository)
        aiRoutes(aiRepository, postRepository, geminiClient, aiAnalysisService)
    }
}
