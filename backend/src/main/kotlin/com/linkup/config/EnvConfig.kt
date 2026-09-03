package com.linkup.config

import io.github.cdimascio.dotenv.Dotenv
import java.nio.file.Files
import java.nio.file.Path

/** Server-only configuration. OS variables override ignored local env files. */
object EnvConfig {
    private val dotenv: Dotenv by lazy { load(findEnvFile(Path.of(""), System.getenv("LINKUP_ENV_FILE")), ".env") }
    private val storageDotenv: Dotenv by lazy { load(runtimeDirectory.resolve(".env.storage"), ".env.storage") }
    private val aiDotenv: Dotenv by lazy { load(runtimeDirectory.resolve(".env.ai"), ".env.ai") }

    val PORT: Int get() = (dotenv["PORT"] ?: "8080").toIntOrNull()
        ?.takeIf { it in 1..65535 } ?: error("PORT must be a number between 1 and 65535.")
    // HOST is commonly injected by shells/CI, so use a project-specific name.
    val HOST: String get() = dotenv["LINKUP_SERVER_HOST"] ?: "0.0.0.0"
    val database: DatabaseConfig by lazy { DatabaseConfig.fromUrl(required("DATABASE_URL")) }
    val JWT_SECRET: String by lazy { required("JWT_SECRET") }
    val JWT_ISSUER: String get() = dotenv["JWT_ISSUER"] ?: "http://0.0.0.0:8080"
    val JWT_AUDIENCE: String get() = dotenv["JWT_AUDIENCE"] ?: "linkup"
    val MEDIA_ROOT: String get() = optional("MEDIA_ROOT") ?: runtimeDirectory.resolve("uploads").toString()
    val PUBLIC_BASE_URL: String get() = optional("PUBLIC_BASE_URL") ?: "http://10.0.2.2:$PORT"
    val GEMINI_API_KEY: String? get() = optional("GEMINI_API_KEY")
    val GEMINI_MODEL: String get() = optional("GEMINI_MODEL") ?: "gemini-3.6-flash"
    val runtimeDirectory: Path get() = findEnvFile(Path.of(""), System.getenv("LINKUP_ENV_FILE")).parent

    fun optional(name: String): String? =
        dotenv[name]?.takeIf(String::isNotBlank)
            ?: storageDotenv[name]?.takeIf(String::isNotBlank)
            ?: aiDotenv[name]?.takeIf(String::isNotBlank)

    private fun required(name: String): String = dotenv[name]?.takeIf(String::isNotBlank)
        ?: error("Missing $name. Configure backend/.env or an OS environment variable; see backend/README.md.")

    private fun load(path: Path, label: String): Dotenv = try {
        Dotenv.configure()
            .directory(path.parent.toString())
            .filename(path.fileName.toString())
            .ignoreIfMissing()
            .load()
    } catch (_: Exception) {
        error("Cannot read backend/$label. Each setting must use KEY=VALUE syntax.")
    }

    internal fun findEnvFile(workingDirectory: Path, explicitFile: String?): Path {
        val root = workingDirectory.toAbsolutePath().normalize()
        if (!explicitFile.isNullOrBlank()) {
            val path = root.resolve(explicitFile).normalize()
            require(Files.isRegularFile(path)) { "LINKUP_ENV_FILE does not point to a readable env file." }
            return path
        }
        val candidates = listOfNotNull(
            root.resolve("backend/.env"),
            root.resolve(".env"),
            root.parent?.resolve("backend/.env"),
            root.parent?.resolve(".env")
        )
        return candidates.firstOrNull(Files::isRegularFile) ?: root.resolve(".env")
    }
}
