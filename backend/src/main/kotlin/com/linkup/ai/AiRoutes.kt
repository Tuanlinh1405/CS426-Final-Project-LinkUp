package com.linkup.ai

import com.linkup.posts.PostFailure
import com.linkup.posts.PostRepository
import com.linkup.routes.currentUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import java.util.UUID

fun Route.aiRoutes(
    repository: AiRepository,
    posts: PostRepository,
    gemini: GeminiClient,
    analysis: AiAnalysisService,
) {
    authenticate {
        route("/ai") {
            post("/posts/{postId}/analyze") {
                call.aiGuard {
                    val userId = call.currentUserId() ?: throw AiFailure(401, "Vui lòng đăng nhập lại.")
                    val postId = uuid(call.parameters["postId"])
                    val post = posts.get(postId, userId) ?: throw AiFailure(404, "Bài viết không còn tồn tại.")
                    val fingerprint = analysis.fingerprint(post)
                    val cached = repository.cachedAnalysis(fingerprint)
                    val titleSource = post.content.trim().ifBlank { "Bài viết có hình ảnh" }
                    val saved = repository.startAnalysis(
                        userId = userId,
                        title = "Phân tích: ${titleSource.take(70)}",
                        prompt = "Hãy phân tích bài viết này của @${post.author.username}.",
                        cachedAnswer = cached,
                    )
                    if (cached == null) {
                        analysis.enqueue(fingerprint, post, UUID.fromString(saved.conversation.id))
                        call.respond(HttpStatusCode.Accepted, saved)
                    } else {
                        call.respond(HttpStatusCode.OK, saved)
                    }
                }
            }

            get("/conversations") {
                call.aiGuard {
                    val userId = call.currentUserId() ?: throw AiFailure(401, "Vui lòng đăng nhập lại.")
                    call.respond(repository.conversations(userId))
                }
            }
            post("/conversations") {
                call.aiGuard {
                    val userId = call.currentUserId() ?: throw AiFailure(401, "Vui lòng đăng nhập lại.")
                    val request = call.receive<AiCreateConversationRequest>()
                    call.respond(HttpStatusCode.Created, repository.createConversation(userId, request.title))
                }
            }

            route("/conversations/{id}") {
                get("/messages") {
                    call.aiGuard {
                        val userId = call.currentUserId() ?: throw AiFailure(401, "Vui lòng đăng nhập lại.")
                        call.respond(repository.messages(uuid(call.parameters["id"]), userId))
                    }
                }
                post("/messages") {
                    call.aiGuard {
                        val userId = call.currentUserId() ?: throw AiFailure(401, "Vui lòng đăng nhập lại.")
                        val conversationId = uuid(call.parameters["id"])
                        val prompt = call.receive<AiPromptRequest>().content.trim()
                        if (prompt.isEmpty() || prompt.length > 2_000) throw AiFailure(400, "Câu hỏi phải có từ 1 đến 2.000 ký tự.")
                        val history = repository.messages(conversationId, userId)
                        val answer = gemini.chat(history, prompt)
                        call.respond(HttpStatusCode.Created, repository.appendExchange(conversationId, userId, prompt, answer))
                    }
                }
            }
        }
    }
}

private fun uuid(value: String?): UUID = runCatching { UUID.fromString(value) }
    .getOrElse { throw AiFailure(400, "Mã nội dung không hợp lệ.") }

private suspend fun io.ktor.server.application.ApplicationCall.aiGuard(block: suspend () -> Unit) {
    try { block() }
    catch (error: CancellationException) { throw error }
    catch (error: AiFailure) { respond(HttpStatusCode.fromValue(error.status), AiError(error.message)) }
    catch (error: PostFailure) { respond(HttpStatusCode.fromValue(error.status), AiError(error.message)) }
    catch (_: Exception) { respond(HttpStatusCode.ServiceUnavailable, AiError("LinkUp AI tạm thời không khả dụng. Vui lòng thử lại.")) }
}
