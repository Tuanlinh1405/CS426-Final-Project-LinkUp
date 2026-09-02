package com.linkup.routes

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import java.util.UUID

/**
 * The authenticated user's id, or null when the token carries no usable claim.
 *
 * Routes behind `authenticate` still check for null: a token that verifies but has
 * a malformed `userId` claim must not be treated as a valid session.
 */
internal fun ApplicationCall.currentUserId(): UUID? =
    principal<JWTPrincipal>()
        ?.payload
        ?.getClaim("userId")
        ?.asString()
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
