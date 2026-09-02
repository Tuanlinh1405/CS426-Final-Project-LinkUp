package com.linkup.storage

/** A file that has been persisted by a [MediaStorage] implementation. */
data class StoredMedia(
    val key: String,
    val url: String,
    val size: Long,
    val contentType: String
)

/**
 * Abstraction over the binary store so the MinIO adapter planned in
 * `docs/ARCHITECTURE_API_DATABASE.md` can replace [LocalMediaStorage] without
 * touching routes or repositories.
 */
interface MediaStorage {
    /** Persists [bytes] under [folder] and returns its public URL. */
    suspend fun put(bytes: ByteArray, contentType: String, folder: String): StoredMedia

    /** Removes a previously stored object. Missing keys are ignored. */
    suspend fun delete(key: String)

    /** Public URL for an already stored [key]. */
    fun urlFor(key: String): String

    /** Recovers the storage key from a URL this store produced, or null if unrecognised. */
    fun keyFromUrl(url: String?): String?

    companion object {
        /** Image types the API accepts for avatar and cover uploads. */
        val ALLOWED_IMAGE_TYPES = mapOf(
            "image/jpeg" to "jpg",
            "image/jpg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
            "image/gif" to "gif"
        )

        const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
    }
}
