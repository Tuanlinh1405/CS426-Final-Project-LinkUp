package com.linkup.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.linkup.config.EnvConfig
import java.util.*

object JwtService {
    private val algorithm = Algorithm.HMAC256(EnvConfig.JWT_SECRET)

    fun generateToken(userId: String): String {
        return JWT.create()
            .withAudience(EnvConfig.JWT_AUDIENCE)
            .withIssuer(EnvConfig.JWT_ISSUER)
            .withClaim("userId", userId)
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000 * 24)) // 24 hours
            .sign(algorithm)
    }

    fun getVerifier() = JWT
        .require(algorithm)
        .withAudience(EnvConfig.JWT_AUDIENCE)
        .withIssuer(EnvConfig.JWT_ISSUER)
        .build()
}
