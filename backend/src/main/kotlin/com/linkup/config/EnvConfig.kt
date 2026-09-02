package com.linkup.config

import io.github.cdimascio.dotenv.Dotenv
import java.nio.file.Files
import java.nio.file.Path

/** Secrets stay on the server. OS environment variables take precedence over the file. */
object EnvConfig {
    private val dotenv: Dotenv by lazy {
        val path = findEnvFile(Path.of(""), System.getenv("LINKUP_ENV_FILE"))
        try {
            Dotenv.configure()
                .directory(path.parent.toString())
                .filename(path.fileName.toString())
                .ignoreIfMissing()
                .load()
        } catch (_: Exception) {
            // Dotenv's original exception can contain the secret-bearing source line.
            error("Cannot read backend/.env. Each setting must use KEY=VALUE syntax.")
        }
    }
    private val storageDotenv: Dotenv by lazy {
        val path = runtimeDirectory.resolve(".env.storage")
        try {
            Dotenv.configure()
                .directory(path.parent.toString())
                .filename(path.fileName.toString())
                .ignoreIfMissing()
                .load()
        } catch (_: Exception) {
            error("Cannot read backend/.env.storage. Each setting must use KEY=VALUE syntax.")
        }
    }

    val PORT: Int get() = (dotenv["PORT"] ?: "8080").toIntOrNull()
        ?.takeIf { it in 1..65535 } ?: error("PORT must be a number between 1 and 65535.")
    // Avoid the generic HOST variable, which is commonly defined by shells/CI and can force loopback.
    val HOST: String get() = dotenv["LINKUP_SERVER_HOST"] ?: "0.0.0.0"
    val database: DatabaseConfig by lazy { DatabaseConfig.fromUrl(required("DATABASE_URL")) }
    val JWT_SECRET: String by lazy { required("JWT_SECRET") }
    val JWT_ISSUER: String get() = dotenv["JWT_ISSUER"] ?: "http://0.0.0.0:8080"
    val JWT_AUDIENCE: String get() = dotenv["JWT_AUDIENCE"] ?: "linkup"
    fun optional(name: String): String? = dotenv[name]?.takeIf(String::isNotBlank)
        ?: storageDotenv[name]?.takeIf(String::isNotBlank)
    val runtimeDirectory: Path get() = findEnvFile(Path.of(""), System.getenv("LINKUP_ENV_FILE")).parent

    private fun required(name: String): String = dotenv[name]?.takeIf { it.isNotBlank() }
        ?: error("Missing $name. Configure backend/.env or set an OS environment variable; see backend/README.md.")

    internal fun findEnvFile(workingDirectory: Path, explicitFile: String?): Path {
        val root = workingDirectory.toAbsolutePath().normalize()
        if (!explicitFile.isNullOrBlank()) {
            val path = root.resolve(explicitFile).normalize()
            require(Files.isRegularFile(path)) { "LINKUP_ENV_FILE does not point to a readable env file." }
            return path
        }
        // Both Gradle runs (backend cwd) and IDE runs (project-root cwd).
        return listOf(root.resolve("backend/.env"), root.resolve(".env"))
            .firstOrNull { Files.isRegularFile(it) } ?: root.resolve(".env")
    }
}
