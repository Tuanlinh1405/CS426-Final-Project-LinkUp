package com.example.linkup.data.repository

import com.example.linkup.data.mapper.toDomain
import com.example.linkup.data.mapper.toDto
import com.example.linkup.data.model.PickedImage
import com.example.linkup.data.model.Profile
import com.example.linkup.data.model.ProfileException
import com.example.linkup.data.model.ProfileUpdate
import com.example.linkup.data.remote.api.ProfileApiService
import com.example.linkup.data.remote.dto.ApiErrorDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val api: ProfileApiService,
    private val json: Json
) : ProfileRepository {

    override suspend fun getMyProfile(): Result<Profile> =
        call { api.getMyProfile() }.map { it.toDomain() }

    override suspend fun getProfile(idOrUsername: String): Result<Profile> =
        call { api.getProfile(idOrUsername.removePrefix("@")) }.map { it.toDomain() }

    override suspend fun updateProfile(update: ProfileUpdate): Result<Profile> {
        if (update.isEmpty) return getMyProfile()
        return call { api.updateProfile(update.toDto()) }.map { it.toDomain() }
    }

    override suspend fun uploadAvatar(image: PickedImage): Result<Profile> =
        call { api.uploadAvatar(image.toPart()) }.flatMap { getMyProfile() }

    override suspend fun uploadCover(image: PickedImage): Result<Profile> =
        call { api.uploadCover(image.toPart()) }.flatMap { getMyProfile() }

    override suspend fun removeAvatar(): Result<Profile> =
        call { api.deleteAvatar() }.map { it.toDomain() }

    override suspend fun removeCover(): Result<Profile> =
        call { api.deleteCover() }.map { it.toDomain() }

    override suspend fun setFollowing(userId: String, follow: Boolean): Result<Boolean> =
        call { if (follow) api.follow(userId) else api.unfollow(userId) }
            .map { it.isFollowing }

    // ---- helpers ---------------------------------------------------------

    private fun PickedImage.toPart(): MultipartBody.Part =
        MultipartBody.Part.createFormData(
            "file",
            fileName,
            bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        )

    /**
     * Runs an API call and normalises every failure into a [ProfileException] so
     * callers never have to inspect HTTP codes or parse bodies themselves.
     */
    private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> = try {
        val response = block()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(response.toProfileException())
        }
    } catch (e: IOException) {
        Result.failure(
            ProfileException("Can't reach the server. Check that the backend is running.")
        )
    } catch (e: Exception) {
        Result.failure(ProfileException(e.message ?: "Something went wrong"))
    }

    private fun Response<*>.toProfileException(): ProfileException {
        val raw = try {
            errorBody()?.string()
        } catch (e: IOException) {
            null
        }

        val parsed = raw?.takeIf { it.isNotBlank() }?.let {
            runCatching { json.decodeFromString<ApiErrorDto>(it) }.getOrNull()
        }
        if (parsed != null) {
            return ProfileException(parsed.message, parsed.fieldErrors)
        }

        val fallback = when (code()) {
            401 -> "Your session expired. Please sign in again."
            403 -> "You don't have permission to do that."
            404 -> "Profile not found."
            413 -> "That image is too large. Pick one under 8 MB."
            415 -> "Use a JPEG, PNG, WebP or GIF image."
            in 500..599 -> "The server had a problem. Try again in a moment."
            else -> raw?.takeIf { it.isNotBlank() } ?: "Something went wrong (HTTP ${code()})."
        }
        return ProfileException(fallback)
    }
}

/** Chains a second call only when the first succeeded. */
private inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> {
    val failure = exceptionOrNull()
    return if (failure != null) Result.failure(failure) else transform(getOrThrow())
}
