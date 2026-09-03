package com.linkup.reels

import com.linkup.config.EnvConfig
import io.minio.*
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.net.URI
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

interface ReelStorage {
    val type: String
    fun put(key: String, file: Path, contentType: String)
    fun open(key: String, offset: Long = 0, length: Long? = null): InputStream
    fun size(key: String): Long
    fun delete(key: String)
    /** A short-lived direct playback URL, or null when this backend must proxy bytes. */
    fun playbackUrl(key: String): String? = null
}

class LocalReelStorage(directory: Path) : ReelStorage {
    override val type = "local"
    private val root = directory.toAbsolutePath().normalize()
    private fun resolve(key: String): Path {
        if (!key.matches(Regex("(reels|posts)/[a-f0-9-]+/[a-f0-9-]+/[a-f0-9-]+\\.(mp4|jpg|jpeg|png|webp)"))) throw ReelFailure(400, "Invalid media key.")
        return root.resolve(key).normalize().also { require(it.startsWith(root)) }
    }
    override fun put(key: String, file: Path, contentType: String) {
        val destination = resolve(key)
        Files.createDirectories(destination.parent)
        Files.copy(file, destination) // Keys are unique: never overwrite an existing media file.
    }
    override fun open(key: String, offset: Long, length: Long?): InputStream = Files.newInputStream(resolve(key)).also { it.skipNBytes(offset) }
    override fun size(key: String): Long = Files.size(resolve(key))
    override fun delete(key: String) { Files.deleteIfExists(resolve(key)) }
}

class MinioReelStorage(endpoint: String, accessKey: String, secretKey: String, private val bucket: String) : ReelStorage {
    override val type = "minio"
    private val client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build()
    override fun put(key: String, file: Path, contentType: String) {
        // Bucket creation belongs to storage setup, not an upload request.
        client.uploadObject(UploadObjectArgs.builder().bucket(bucket).`object`(key).filename(file.toString()).contentType(contentType).build())
    }
    override fun open(key: String, offset: Long, length: Long?): InputStream {
        val request = GetObjectArgs.builder().bucket(bucket).`object`(key).offset(offset)
        if (length != null) request.length(length)
        return client.getObject(request.build())
    }
    override fun size(key: String): Long = client.statObject(StatObjectArgs.builder().bucket(bucket).`object`(key).build()).size()
    override fun delete(key: String) { client.removeObject(RemoveObjectArgs.builder().bucket(bucket).`object`(key).build()) }
}

class SupabaseReelStorage(
    endpoint: String,
    region: String,
    accessKey: String,
    secretKey: String,
    private val bucket: String,
) : ReelStorage {
    override val type = "supabase"
    private val storageEndpoint = URI.create(endpoint)
    private val storageRegion = Region.of(region)
    private val credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
    private val s3Configuration = S3Configuration.builder().pathStyleAccessEnabled(true).build()
    private val client = S3Client.builder()
        .endpointOverride(storageEndpoint)
        .region(storageRegion)
        .credentialsProvider(credentials)
        .serviceConfiguration(s3Configuration)
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .build()
    private val presigner = S3Presigner.builder()
        .endpointOverride(storageEndpoint)
        .region(storageRegion)
        .credentialsProvider(credentials)
        .serviceConfiguration(s3Configuration)
        .build()

    override fun put(key: String, file: Path, contentType: String) {
        client.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
            RequestBody.fromFile(file),
        )
    }

    override fun open(key: String, offset: Long, length: Long?): InputStream {
        val request = GetObjectRequest.builder().bucket(bucket).key(key)
        if (offset > 0 || length != null) {
            val end = length?.let { offset + it - 1 }
            request.range(if (end == null) "bytes=$offset-" else "bytes=$offset-$end")
        }
        return client.getObject(request.build())
    }

    override fun size(key: String): Long = client.headObject(
        HeadObjectRequest.builder().bucket(bucket).key(key).build(),
    ).contentLength()

    override fun delete(key: String) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
    }

    override fun playbackUrl(key: String): String {
        val request = GetObjectRequest.builder().bucket(bucket).key(key).build()
        return presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(request)
                .build(),
        ).url().toString()
    }
}

class ReelStorageRegistry(private val supplied: ReelStorage? = null) {
    private val stores = mutableMapOf<String, ReelStorage>()
    fun current(): ReelStorage = supplied ?: get(EnvConfig.optional("REELS_STORAGE") ?: "minio")
    @Synchronized fun get(type: String): ReelStorage {
        if (supplied?.type == type) return supplied
        return stores.getOrPut(type) {
            when (type) {
                "local" -> LocalReelStorage(EnvConfig.runtimeDirectory.resolve(EnvConfig.optional("REELS_LOCAL_DIR") ?: ".reels-media"))
                "minio" -> MinioReelStorage(
                    EnvConfig.optional("MINIO_ENDPOINT") ?: throw ReelFailure(503, "MinIO is not configured."),
                    EnvConfig.optional("MINIO_ACCESS_KEY") ?: throw ReelFailure(503, "MinIO credentials are missing."),
                    EnvConfig.optional("MINIO_SECRET_KEY") ?: throw ReelFailure(503, "MinIO credentials are missing."),
                    EnvConfig.optional("MINIO_BUCKET_NAME") ?: "linkup",
                )
                "supabase" -> SupabaseReelStorage(
                    EnvConfig.optional("SUPABASE_STORAGE_S3_ENDPOINT") ?: throw ReelFailure(503, "Supabase Storage endpoint is missing."),
                    EnvConfig.optional("SUPABASE_STORAGE_S3_REGION") ?: throw ReelFailure(503, "Supabase Storage region is missing."),
                    EnvConfig.optional("SUPABASE_STORAGE_S3_ACCESS_KEY_ID") ?: throw ReelFailure(503, "Supabase Storage credentials are missing."),
                    EnvConfig.optional("SUPABASE_STORAGE_S3_SECRET_ACCESS_KEY") ?: throw ReelFailure(503, "Supabase Storage credentials are missing."),
                    EnvConfig.optional("SUPABASE_STORAGE_BUCKET") ?: "linkup-media",
                )
                else -> throw ReelFailure(503, "REELS_STORAGE must be local, minio or supabase.")
            }
        }
    }
}
