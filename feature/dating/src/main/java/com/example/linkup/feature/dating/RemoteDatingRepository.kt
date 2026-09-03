package com.example.linkup.feature.dating

import com.example.linkup.data.model.User
import javax.inject.Inject

/** Maps the backend contract to Dating domain models. */
class RemoteDatingRepository @Inject constructor(
    private val api: DatingApiService
) : DatingRepository {
    override suspend fun getProfile(): DatingProfile? = api.getProfile().body()?.toDomain()

    override suspend fun updateProfile(profile: DatingProfile): DatingProfile {
        return api.updateProfile(profile.toRequest()).requireBody().toDomain()
    }

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
        // PASS history is persisted by the backend; it is not reset locally.
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
        maxAge = maxAge
    )

    private fun DatingCandidateResponseDto.toDomain() = DatingCandidate(
        user = User(userId, name, username, initials, bio.orEmpty()),
        age = age ?: 0,
        bio = bio.orEmpty(),
        interests = interests,
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
