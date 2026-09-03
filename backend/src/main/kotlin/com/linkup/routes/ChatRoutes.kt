package com.linkup.routes

import com.linkup.model.ApiError
import com.linkup.model.CreateDirectConversationRequest
import com.linkup.model.CreateGroupConversationRequest
import com.linkup.model.MediaUploadResponse
import com.linkup.model.PresenceResponse
import com.linkup.model.SendMessageRequest
import com.linkup.model.WebSocketFrame
import com.linkup.repository.ChatRepository
import com.linkup.repository.UserRepository
import com.linkup.service.JwtService
import com.linkup.storage.MediaStorage
import com.linkup.websocket.ChatWebSocketManager
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.readRemaining
import io.ktor.websocket.*
import kotlinx.io.readByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private const val DEFAULT_MESSAGE_PAGE = 50
private const val CHAT_MEDIA_FOLDER = "chat"

fun Route.chatRoutes(
    chatRepository: ChatRepository,
    wsManager: ChatWebSocketManager,
    userRepository: UserRepository,
    mediaStorage: MediaStorage,
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
                    wsManager.notifyConversationCreated(
                        UUID.fromString(conversation.id),
                        listOf(userId, targetUserId),
                        excludeUserId = userId
                    )
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
                wsManager.notifyConversationCreated(
                    UUID.fromString(conversation.id),
                    (memberUuids + userId).distinct(),
                    excludeUserId = userId
                )
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

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_MESSAGE_PAGE
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L

                if (!chatRepository.isConversationMember(convId, userId)) {
                    call.respond(HttpStatusCode.Forbidden, "You are not a member of this conversation")
                    return@get
                }

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
                    tempId = request.tempId,
                    mediaUrl = request.mediaUrl
                )

                if (message == null) {
                    call.respond(HttpStatusCode.Forbidden, "You are not a member of this conversation")
                    return@post
                }

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

                if (!chatRepository.isConversationMember(convId, userId)) {
                    call.respond(HttpStatusCode.Forbidden, "You are not a member of this conversation")
                    return@post
                }

                wsManager.handleMarkRead(userId, convId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "success"))
            }

            // POST /conversations/{id}/media - Upload an image to send in this conversation
            post("/{id}/media") {
                val userId = getUserIdFromCall(call) ?: run {
                    call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                    return@post
                }

                val convId = call.conversationId() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ApiError("Invalid conversation ID"))
                    return@post
                }

                if (!chatRepository.isConversationMember(convId, userId)) {
                    call.respond(HttpStatusCode.Forbidden, ApiError("You are not a member of this conversation"))
                    return@post
                }

                uploadChatImage(mediaStorage)
            }

            // GET /conversations/{id}/presence - Who is online right now
            get("/{id}/presence") {
                val userId = getUserIdFromCall(call) ?: run {
                    call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                    return@get
                }

                val convId = call.conversationId() ?: run {
                    call.respond(HttpStatusCode.BadRequest, ApiError("Invalid conversation ID"))
                    return@get
                }

                val memberIds = chatRepository.getConversationMemberIds(convId)
                if (userId !in memberIds) {
                    call.respond(HttpStatusCode.Forbidden, ApiError("You are not a member of this conversation"))
                    return@get
                }

                val online = memberIds
                    .filter { it != userId && wsManager.isUserOnline(it) }
                    .map { it.toString() }
                call.respond(HttpStatusCode.OK, PresenceResponse(online))
            }

            // DELETE /conversations/{id}/messages/{messageId} - Unsend your own message
            delete("/{id}/messages/{messageId}") {
                val userId = getUserIdFromCall(call) ?: run {
                    call.respond(HttpStatusCode.Unauthorized, ApiError("Not signed in"))
                    return@delete
                }

                val convId = call.conversationId()
                val msgId = runCatching { UUID.fromString(call.parameters["messageId"]) }.getOrNull()
                if (convId == null || msgId == null) {
                    call.respond(HttpStatusCode.BadRequest, ApiError("Invalid conversation or message ID"))
                    return@delete
                }

                // deleteMessage scopes the DELETE to this sender, so a non-sender simply
                // matches no row — no extra lookup needed to reject them.
                if (!chatRepository.deleteMessage(convId, msgId, userId)) {
                    call.respond(HttpStatusCode.Forbidden, ApiError("You can only delete your own messages"))
                    return@delete
                }

                wsManager.handleDeleteMessage(convId, msgId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
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
                            val mediaUrl = wsFrame.message?.mediaUrl
                            val tempId = wsFrame.tempId ?: wsFrame.message?.id

                            if (convIdStr != null) {
                                val convId = try { UUID.fromString(convIdStr) } catch (e: Exception) { null }
                                if (convId != null) {
                                    wsManager.handleSendMessage(
                                        senderId = userId,
                                        conversationId = convId,
                                        textContent = textContent,
                                        type = msgType,
                                        tempId = tempId,
                                        mediaUrl = mediaUrl
                                    ) ?: send(
                                        Frame.Text(
                                            json.encodeToString(
                                                WebSocketFrame(
                                                    event = "ERROR",
                                                    error = "You are not a member of this conversation"
                                                )
                                            )
                                        )
                                    )
                                }
                            }
                        }

                        "MARK_READ" -> {
                            val convIdStr = wsFrame.conversationId
                            if (convIdStr != null) {
                                val convId = try { UUID.fromString(convIdStr) } catch (e: Exception) { null }
                                if (convId != null && chatRepository.isConversationMember(convId, userId)) {
                                    wsManager.handleMarkRead(userId, convId)
                                }
                            }
                        }

                        "TYPING" -> {
                            val convIdStr = wsFrame.conversationId
                            if (convIdStr != null) {
                                val convId = try { UUID.fromString(convIdStr) } catch (e: Exception) { null }
                                if (convId != null && chatRepository.isConversationMember(convId, userId)) {
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

/** Parses and validates the `{id}` path segment as a conversation UUID. */
private fun ApplicationCall.conversationId(): UUID? =
    runCatching { UUID.fromString(parameters["id"]) }.getOrNull()

/**
 * Handles a chat image upload (mirror of the profile `uploadImage`, but writes to the
 * `chat` folder and returns only the stored media so the message row can hold its URL).
 */
private suspend fun RoutingContext.uploadChatImage(storage: MediaStorage) {
    val declaredLength = call.request.headers["Content-Length"]?.toLongOrNull()
    if (declaredLength != null && declaredLength > MediaStorage.MAX_IMAGE_BYTES * 2) {
        return call.respond(HttpStatusCode.PayloadTooLarge, ApiError("Image must be 8 MB or smaller"))
    }

    var bytes: ByteArray? = null
    var contentType: String? = null

    try {
        call.receiveMultipart().forEachPart { part ->
            if (part is PartData.FileItem && bytes == null) {
                contentType = part.contentType?.let { "${it.contentType}/${it.contentSubtype}" }
                bytes = part.provider().readRemaining().readByteArray()
            }
            part.dispose()
        }
    } catch (e: Exception) {
        return call.respond(HttpStatusCode.BadRequest, ApiError("Could not read the uploaded file"))
    }

    val payload = bytes
        ?: return call.respond(HttpStatusCode.BadRequest, ApiError("No image was attached"))

    if (payload.isEmpty()) {
        return call.respond(HttpStatusCode.BadRequest, ApiError("The uploaded image is empty"))
    }
    if (payload.size > MediaStorage.MAX_IMAGE_BYTES) {
        return call.respond(HttpStatusCode.PayloadTooLarge, ApiError("Image must be 8 MB or smaller"))
    }

    val resolvedType = contentType?.lowercase()
    if (resolvedType == null || resolvedType !in MediaStorage.ALLOWED_IMAGE_TYPES) {
        return call.respond(
            HttpStatusCode.UnsupportedMediaType,
            ApiError("Use a JPEG, PNG, WebP or GIF image")
        )
    }

    val stored = storage.put(payload, resolvedType, CHAT_MEDIA_FOLDER)
    call.respond(
        HttpStatusCode.OK,
        MediaUploadResponse(
            url = stored.url,
            key = stored.key,
            size = stored.size,
            contentType = stored.contentType
        )
    )
}
