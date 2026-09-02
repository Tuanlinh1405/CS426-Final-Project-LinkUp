package com.linkup.storage

import com.linkup.config.EnvConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Disk-backed [MediaStorage]. Files land under `EnvConfig.MEDIA_ROOT` and are served
 * by the `/media` static route registered in `Application.module()`.
 *
 * This keeps avatar and cover uploads working without a MinIO container; swapping in
 * MinIO later only means providing another [MediaStorage] to the routes.
 */
class LocalMediaStorage(
    private val rootDir: File = File(EnvConfig.MEDIA_ROOT),
    private val publicBaseUrl: String = EnvConfig.PUBLIC_BASE_URL
) : MediaStorage {

    init {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    override suspend fun put(bytes: ByteArray, contentType: String, folder: String): StoredMedia =
        withContext(Dispatchers.IO) {
            val extension = MediaStorage.ALLOWED_IMAGE_TYPES[contentType.lowercase()]
                ?: throw IllegalArgumentException("Unsupported content type: $contentType")

            val key = "$folder/${UUID.randomUUID()}.$extension"
            val target = File(rootDir, key)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)

            StoredMedia(
                key = key,
                url = urlFor(key),
                size = bytes.size.toLong(),
                contentType = contentType
            )
        }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        // Reject traversal before touching the filesystem.
        val target = File(rootDir, key).canonicalFile
        if (target.path.startsWith(rootDir.canonicalFile.path) && target.isFile) {
            target.delete()
        }
        Unit
    }

    override fun urlFor(key: String): String = "${publicBaseUrl.trimEnd('/')}/media/$key"

    override fun keyFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val marker = "/media/"
        val index = url.indexOf(marker)
        return if (index >= 0) url.substring(index + marker.length) else null
    }
}
