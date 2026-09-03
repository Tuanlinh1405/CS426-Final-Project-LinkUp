package com.linkup.reels

import java.nio.file.Files
import java.util.UUID
import kotlin.system.exitProcess

/** Explicit diagnostic: writes one small random object, verifies it, then removes it. */
fun storageCheck() {
    val id = UUID.randomUUID()
    val key = "reels/$id/$id/${UUID.randomUUID()}.mp4"
    val content = "linkup-storage-check".encodeToByteArray()
    val file = Files.createTempFile("linkup-storage-check-", ".mp4")
    var uploaded = false
    var successful = false
    try {
        Files.write(file, content)
        val storage = ReelStorageRegistry().current()
        check(storage.type == "supabase") { "REELS_STORAGE is not configured as supabase." }
        storage.put(key, file, "video/mp4")
        uploaded = true
        check(storage.size(key) == content.size.toLong()) { "Uploaded object size does not match." }
        storage.open(key).use { check(it.readBytes().contentEquals(content)) { "Uploaded object content does not match." } }
        storage.open(key, 2, 5).use { check(it.readBytes().contentEquals(content.copyOfRange(2, 7))) { "Byte range does not match." } }
        storage.delete(key)
        uploaded = false
        successful = true
        println("Supabase Storage: upload, size, download, byte range and delete OK.")
    } catch (_: Exception) {
        System.err.println(if (uploaded) "Storage check failed; cleanup will be retried." else "Storage check failed; credentials, bucket and S3 settings need review.")
    } finally {
        if (uploaded) runCatching { ReelStorageRegistry().current().delete(key) }
        Files.deleteIfExists(file)
    }
    if (!successful) exitProcess(1)
}

fun main() = storageCheck()
