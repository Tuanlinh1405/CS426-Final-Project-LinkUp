package com.linkup.config

import io.github.cdimascio.dotenv.dotenv
import java.io.File

object EnvConfig {
    private val envDir = if (File(".env").exists()) "." else if (File("../.env").exists()) ".." else "."

    private val dotenv = dotenv {
        ignoreIfMissing = true
        directory = envDir
    }

    val PORT = dotenv["PORT"]?.toInt() ?: 8080
    val HOST = dotenv["HOST"] ?: "0.0.0.0"

    val DATABASE_URL = dotenv["DATABASE_URL"]
        ?: "postgresql://postgres.hoxujmjicfveykawiwvk:LinkUp-DBCS426@aws-0-ap-southeast-2.pooler.supabase.com:5432/postgres"

    val JWT_SECRET = dotenv["JWT_SECRET"] ?: "linkup_cs426_backend_signature_secret_key_2026!@#"
    val JWT_ISSUER = dotenv["JWT_ISSUER"] ?: "http://10.0.2.2:8080"
    val JWT_AUDIENCE = dotenv["JWT_AUDIENCE"] ?: "linkup_android_clients"

    /** Directory that backs [com.linkup.storage.LocalMediaStorage]. */
    val MEDIA_ROOT = dotenv["MEDIA_ROOT"] ?: "uploads"

    /**
     * Origin the Android client uses to fetch uploaded media. Must be reachable from the
     * device: `10.0.2.2` is the emulator's alias for the host machine.
     */
    val PUBLIC_BASE_URL = dotenv["PUBLIC_BASE_URL"] ?: "http://10.0.2.2:$PORT"
}
