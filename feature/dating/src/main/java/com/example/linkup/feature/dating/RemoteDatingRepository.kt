package com.example.linkup.feature.dating

import com.example.linkup.data.model.PickedImage
import com.example.linkup.data.model.User
import com.example.linkup.data.network.ApiClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/** Maps the backend contract to Dating domain models. */
class RemoteDatingRepository @Inject constructor(
    private val api: DatingApiService
) : DatingRepository {
    override suspend fun getProfile(): DatingProfile? = api.getProfile().body()?.toDomain()

    override suspend fun updateProfile(profile: DatingProfile): DatingProfile {
        return api.updateProfile(profile.toRequest()).requireBody().toDomain()
    }

    override suspend fun uploadPhoto(image: PickedImage): List<DatingPhoto> =
        api.uploadPhoto(image.toPart()).requireBody().map { it.toDomain() }

    override suspend fun deletePhoto(photoId: String): List<DatingPhoto> =
        api.deletePhoto(photoId).requireBody().map { it.toDomain() }

    override suspend fun getDiscoverCandidates(): List<DatingCandidate> =
        api.getDiscoverCandidates().requireBody().map { it.toDomain() }

    override suspend fun swipe(targetUserId: String, decision: SwipeDecision): SwipeResult {
        val response = api.swipe(SwipeRequestDto(targetUserId, decision.name)).requireBody()
        return SwipeResult(
            decision = decision,
            isMatch = response.isMatch,
            match = response.match?.toDomain()
        )
    }

    override suspend fun getMatches(): List<DatingMatch> =
        api.getMatches().requireBody().map { it.toDomain() }

    override suspend fun resetPassedCandidates() {
        val response = api.resetPassedSwipes()
        check(response.isSuccessful) { "Dating API request failed: ${response.code()}" }
    }

    private fun DatingProfile.toRequest() = DatingProfileRequestDto(
        bio = bio,
        interests = interests,
        lookingFor = lookingFor.name,
        preferredGender = preferredGender,
        minAge = minAge,
        maxAge = maxAge
    )

    private fun DatingProfileResponseDto.toDomain() = DatingProfile(
        userId = userId.orEmpty(),
        bio = bio.orEmpty(),
        interests = interests,
        lookingFor = runCatching { LookingFor.valueOf(lookingFor) }.getOrDefault(LookingFor.RELATIONSHIP),
        preferredGender = preferredGender,
        minAge = minAge,
        maxAge = maxAge,
        photos = photos.map { it.toDomain() },
        name = name.orEmpty(),
        username = username.orEmpty(),
        initials = initials.orEmpty(),
        age = age ?: 0,
        avatarUrl = avatarUrl?.let(ApiClient::mediaUrl)
    )

    private fun DatingPhotoDto.toDomain() = DatingPhoto(
        id = id,
        photoUrl = ApiClient.mediaUrl(photoUrl),
        displayOrder = displayOrder
    )

    private fun PickedImage.toPart(): MultipartBody.Part =
        MultipartBody.Part.createFormData(
            "file",
            fileName,
            bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        )

    private fun DatingCandidateResponseDto.toDomain() = DatingCandidate(
        user = User(userId, name, username, initials, bio.orEmpty()),
        age = age ?: 0,
        bio = bio.orEmpty(),
        interests = interests,
        photoUrl = photoUrl?.let(ApiClient::mediaUrl),
        likedYou = likedYou,
        compatibilityScore = compatibilityScore
    )

    private fun MatchResponseDto.toDomain() = DatingMatch(
        id = id,
        user = User(userId, name, username, initials),
        createdAt = createdAt
    )

    private fun <T> retrofit2.Response<T>.requireBody(): T {
        check(isSuccessful) { "Dating API request failed: ${this.code()}" }
        return body() ?: error("Dating API returned an empty response")
    }
}
