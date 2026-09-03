package com.linkup.search

import com.linkup.posts.PostFailure
import com.linkup.reels.ReelFailure
import com.linkup.reels.ReelStorageRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.CancellationException
import java.sql.SQLException
import java.util.UUID

fun Route.searchRoutes(repository: SearchRepository, storage: ReelStorageRegistry = ReelStorageRegistry()) {
    authenticate {
        route("/search") {
            get {
                call.searchGuard {
                    val viewer = call.searchUser()
                    val results = repository.search(
                        viewer,
                        call.request.queryParameters["q"].orEmpty(),
                        call.request.queryParameters["type"] ?: "all",
                        call.request.queryParameters["cursor"],
                        call.request.queryParameters["limit"]?.toIntOrNull() ?: 20,
                    )
                    call.respond(results.directMedia(storage))
                }
            }
        }
    }
}

private fun SearchResults.directMedia(stores: ReelStorageRegistry): SearchResults = copy(
    posts = posts.map { post ->
        val direct = post.imageStorageKey?.let { stores.current().playbackUrl(it) }
        if (direct == null) post else post.copy(imageUrl = direct)
    },
    reels = reels.map { reel ->
        val store = reel.storageBackend?.let(stores::get)
        if (store == null) reel else reel.copy(
            videoUrl = reel.videoKey?.let(store::playbackUrl) ?: reel.videoUrl,
            thumbnailUrl = reel.thumbnailKey?.let(store::playbackUrl) ?: reel.thumbnailUrl,
        )
    },
)

private fun ApplicationCall.searchUser(): UUID {
    val value = principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString() ?: throw SearchFailure(401, "Please sign in again.")
    return try { UUID.fromString(value) } catch (_: Exception) { throw SearchFailure(401, "Please sign in again.") }
}

private suspend fun ApplicationCall.searchGuard(block: suspend () -> Unit) {
    try { block() }
    catch (error: CancellationException) { throw error }
    catch (error: SearchFailure) { respond(HttpStatusCode.fromValue(error.status), SearchError(error.message)) }
    catch (error: PostFailure) { respond(HttpStatusCode.fromValue(error.status), SearchError(error.message)) }
    catch (error: ReelFailure) { respond(HttpStatusCode.fromValue(error.status), SearchError(error.message)) }
    catch (_: SQLException) { respond(HttpStatusCode.ServiceUnavailable, SearchError("Search database is temporarily unavailable.")) }
    catch (_: Exception) { respond(HttpStatusCode.ServiceUnavailable, SearchError("Search is temporarily unavailable.")) }
}
