package com.linkup.config

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Properties

/** Not a data class: generated toString/copy methods must not expose credentials. */
class DatabaseConfig private constructor(
    val jdbcUrl: String,
    val username: String,
    val password: String,
) {
    fun connectionProperties(): Properties = Properties().apply {
        setProperty("user", username)
        setProperty("password", password)
    }

    override fun toString(): String = "DatabaseConfig([REDACTED])"

    companion object {
        fun fromUrl(value: String): DatabaseConfig {
            try {
                val uri = URI(value.trim())
                require(uri.scheme in setOf("postgres", "postgresql"))
                require(!uri.host.isNullOrBlank() && uri.fragment == null)
                require(!uri.rawPath.isNullOrBlank() && uri.rawPath != "/")
                val credentials = requireNotNull(uri.rawUserInfo).split(':', limit = 2)
                require(credentials.size == 2 && credentials.all { it.isNotBlank() })
                val port = if (uri.port == -1) 5432 else uri.port
                require(port in 1..65535)
                val parameters = linkedMapOf<String, String>()
                uri.rawQuery?.split('&')?.filter { it.isNotBlank() }?.forEach { entry ->
                    val pair = entry.split('=', limit = 2)
                    require(pair.size == 2)
                    val key = decode(pair[0])
                    require(key.lowercase() !in setOf("user", "password"))
                    require(key !in parameters)
                    parameters[key] = decode(pair[1])
                }
                parameters.putIfAbsent("sslmode", "require")
                val local = uri.host in setOf("localhost", "127.0.0.1", "[::1]", "::1")
                require(parameters["sslmode"] in setOf("require", "verify-ca", "verify-full") ||
                    (local && parameters["sslmode"] == "disable"))
                parameters.putIfAbsent("connectTimeout", "10")
                parameters.putIfAbsent("socketTimeout", "30")
                // Supabase transaction pooler does not support prepared statements.
                if (port == 6543) parameters["prepareThreshold"] = "0"
                val query = parameters.entries.joinToString("&") { (key, setting) ->
                    "${encode(key)}=${encode(setting)}"
                }
                return DatabaseConfig(
                    jdbcUrl = "jdbc:postgresql://${uri.host}:$port${uri.rawPath}?$query",
                    username = decode(credentials[0]),
                    password = decode(credentials[1]),
                )
            } catch (_: Exception) {
                // URI/driver exceptions can otherwise repeat the full input, including password.
                throw IllegalArgumentException(
                    "Invalid DATABASE_URL. Use postgresql://USER:PASSWORD@HOST:PORT/DATABASE; " +
                        "percent-encode special characters in credentials and use SSL for remote databases.",
                )
            }
        }

        private fun decode(value: String): String = URLDecoder.decode(value.replace("+", "%2B"), UTF_8)
        private fun encode(value: String): String = URLEncoder.encode(value, UTF_8)
    }
}
