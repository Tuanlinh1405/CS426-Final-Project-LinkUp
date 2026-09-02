package com.linkup.routes

import com.linkup.model.CreateDirectConversationRequest
import com.linkup.model.CreateGroupConversationRequest
import com.linkup.model.SendMessageRequest
import com.linkup.model.WebSocketFrame
import com.linkup.repository.ChatRepository
import com.linkup.repository.UserRepository
import com.linkup.service.JwtService
import com.linkup.websocket.ChatWebSocketManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun Route.chatRoutes(
    chatRepository: ChatRepository,
    wsManager: ChatWebSocketManager,
    userRepository: UserRepository,
) {

    // Authenticated REST APIs
    authenticate {
        route("/conversations") {

            // GET /conversations - List all conversations for authenticated user
            get {
                val userId = getUserIdFromCall(call) ?: run {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    return@get
                }

                val conversations = chatRepository.getConversationsForUser(userId)
                call.respond(HttpStatusCode.OK, conversations)
            }

            // POST /conversations/direct - Get or create 1-on-1 conversation
            post("/direct") {
                val userId = getUserIdFromCall(call) ?: run {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    return@post
                }

                val request = try {
                    call.receive<CreateDirectConversationRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid request body: ${e.message}")
                    return@post
                }

                val targetUserId = try {
                    UUID.fromString(request.targetUserId)
                } catch (e: Exception) {
                    val targetUser = userRepository.getUserByUsername(request.targetUserId)
                        ?: userRepository.getUserByEmail(request.targetUserId)
                    targetUser?.id?.value
                }

                if (targetUserId == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Target user not found by ID, username, or email: ${request.targetUserId}")
                    )
                    return@post
                }

                try {
                    val conversation = chatRepository.getOrCreateDirectConversation(userId, targetUserId)
                    call.respond(HttpStatusCode.OK, conversation)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Could not create conversation"))
                    )
                }
            }

            // POST /conversations/group - Create a new group conversation
            post("/group") {
                val userId = getUserIdFromCall(call) ?: run {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    return@post
                }

                val request = try {
                    call.receive<CreateGroupConversationRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                    return@post
                }

                val memberUuids = try {
                    request.memberUserIds.map { UUID.fromString(it) }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid member user ID format")
                    return@post
                }

                val conversation = chatRepository.createGroupConversation(userId, request.name, memberUuids)
                call.respond(HttpStatusCode.Created, conversation)
            }

            // GET /conversations/{id}/messages - Fetch message history
            get("/{id}/messages") {
                val userId = getUserIdFromCall(call) ?: run {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    return@get
                }

                val convIdStr = call.parameters["id"]
                val convId = try {
                    UUID.fromString(convIdStr)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid conversation ID")
                    return@get
                }

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L

                val messages = chatRepository.getMessagesForConversation(convId, userId, limit, offset)
                call.respond(HttpStatusCode.OK, messages)
            }

            // POST /conversations/{id}/messages - REST Send Message fallback
            post("/{id}/messages") {
                val userId = getUserIdFromCall(call) ?: run {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    return@post
                }

                val convIdStr = call.parameters["id"]
                val convId = try {
                    UUID.fromString(convIdStr)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid conversation ID")
                    return@post
                }

                val request = try {
                    call.receive<SendMessageRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid request body")
                    return@post
                }

                val message = wsManager.handleSendMessage(
                    senderId = userId,
                    conversationId = convId,
                    textContent = request.textContent,
                    type = request.type,
                    tempId = request.tempId
                )

                call.respond(HttpStatusCode.Created, message)
            }

            // POST /conversations/{id}/read - Mark all messages as read
            post("/{id}/read") {
                val userId = getUserIdFromCall(call) ?: run {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                    return@post
                }

                val convIdStr = call.parameters["id"]
                val convId = try {
                    UUID.fromString(convIdStr)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid conversation ID")
                    return@post
                }

                wsManager.handleMarkRead(userId, convId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
            }
        }
    }

    // WebSocket Realtime Chat Endpoint
    webSocket("/chat/ws") {
        val token = call.request.queryParameters["token"]
            ?: call.request.headers["Authorization"]?.removePrefix("Bearer ")

        if (token.isNullOrEmpty()) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Token missing"))
            return@webSocket
        }

        val userId = getUserIdFromToken(token)
        if (userId == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
            return@webSocket
        }

        wsManager.onUserConnected(userId, this)

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val wsFrame = try {
                        json.decodeFromString<WebSocketFrame>(text)
                    } catch (e: Exception) {
                        send(Frame.Text(json.encodeToString(WebSocketFrame(event = "ERROR", error = "Invalid frame format: ${e.message}"))))
                        continue
                    }

                    when (wsFrame.event) {
                        "SEND_MESSAGE" -> {
                            val convIdStr = wsFrame.conversationId
                            val textContent = wsFrame.message?.textContent
                            val msgType = wsFrame.message?.type ?: "TEXT"
                            val tempId = wsFrame.tempId ?: wsFrame.message?.id

                            if (convIdStr != null) {
                                val convId = try { UUID.fromString(convIdStr) } catch (e: Exception) { null }
                                if (convId != null) {
                                    wsManager.handleSendMessage(
                                        senderId = userId,
                                        conversationId = convId,
                                        textContent = textContent,
                                        type = msgType,
                                        tempId = tempId
                                    )
                                }
                            }
                        }

                        "MARK_READ" -> {
                            val convIdStr = wsFrame.conversationId
                            if (convIdStr != null) {
                                val convId = try { UUID.fromString(convIdStr) } catch (e: Exception) { null }
                                if (convId != null) {
                                    wsManager.handleMarkRead(userId, convId)
                                }
                            }
                        }

                        "TYPING" -> {
                            val convIdStr = wsFrame.conversationId
                            if (convIdStr != null) {
                                val convId = try { UUID.fromString(convIdStr) } catch (e: Exception) { null }
                                if (convId != null) {
                                    val typingFrame = wsFrame.copy(senderId = userId.toString())
                                    wsManager.broadcastToConversation(convId, typingFrame, excludeUserId = userId)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Connection exception handling
        } finally {
            wsManager.onUserDisconnected(userId, this)
        }
    }
}

private fun getUserIdFromCall(call: ApplicationCall): UUID? {
    val principal = call.principal<JWTPrincipal>()
    val userIdStr = principal?.payload?.getClaim("userId")?.asString()
    return if (userIdStr != null) {
        try { UUID.fromString(userIdStr) } catch (e: Exception) { null }
    } else null
}

private fun getUserIdFromToken(token: String): UUID? {
    return try {
        val verifier = JwtService.getVerifier()
        val decoded = verifier.verify(token)
        val userIdStr = decoded.getClaim("userId")?.asString()
        if (userIdStr != null) UUID.fromString(userIdStr) else null
    } catch (e: Exception) {
        null
    }
}
