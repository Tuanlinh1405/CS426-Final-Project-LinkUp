package com.example.linkup.feature.dating

import com.example.linkup.data.model.PickedImage
import com.example.linkup.data.model.User

interface DatingRepository {
    suspend fun getProfile(): DatingProfile?
    suspend fun updateProfile(profile: DatingProfile): DatingProfile
    suspend fun uploadPhoto(image: PickedImage): List<DatingPhoto>
    suspend fun deletePhoto(photoId: String): List<DatingPhoto>
    suspend fun getDiscoverCandidates(): List<DatingCandidate>
    suspend fun swipe(targetUserId: String, decision: SwipeDecision): SwipeResult
    suspend fun getMatches(): List<DatingMatch>
    suspend fun resetPassedCandidates()
}

/** In-memory dating source for the MVP UI and unit tests. */
class FakeDatingRepository(
    private val currentUser: User = User("u1", "Sarah Jones", "@sarah.j", "SJ"),
    initialCandidates: List<DatingCandidate> = defaultCandidates(currentUser.id)
) : DatingRepository {
    private var profile = DatingProfile(
        userId = currentUser.id,
        bio = "Designer, weekend hiker, and coffee lover.",
        interests = listOf("Travel", "Design", "Coffee"),
        lookingFor = LookingFor.RELATIONSHIP,
        preferredGender = "ANY",
        minAge = 20,
        maxAge = 30
    )
    private val candidates = initialCandidates.toMutableList()
    private val swipes = mutableMapOf<String, SwipeDecision>()
    private val passedThisSession = mutableSetOf<String>()
    private val matches = mutableListOf<DatingMatch>()

    override suspend fun getProfile(): DatingProfile = profile

    override suspend fun updateProfile(profile: DatingProfile): DatingProfile {
        require(profile.userId == currentUser.id) { "Profile belongs to another user" }
        this.profile = profile
        return profile
    }

    override suspend fun uploadPhoto(image: PickedImage): List<DatingPhoto> {
        val photo = DatingPhoto("photo-${profile.photos.size + 1}", image.fileName, profile.photos.size)
        profile = profile.copy(photos = profile.photos + photo)
        return profile.photos
    }

    override suspend fun deletePhoto(photoId: String): List<DatingPhoto> {
        profile = profile.copy(photos = profile.photos.filterNot { it.id == photoId })
        return profile.photos
    }

    override suspend fun getDiscoverCandidates(): List<DatingCandidate> {
        return candidates
            .asSequence()
            .filter { it.user.id != currentUser.id }
            .filter { it.user.id !in passedThisSession }
            .filter { swipes[it.user.id] != SwipeDecision.LIKE }
            .filter { candidate -> matches.none { it.user.id == candidate.user.id } }
            .map { candidate -> candidate.copy(likedYou = candidate.user.id in candidatesWhoLikedCurrentUser()) }
            .map { candidate -> candidate.copy(compatibilityScore = calculateScore(candidate)) }
            .sortedWith(compareByDescending<DatingCandidate> { it.likedYou }.thenByDescending { it.compatibilityScore })
            .toList()
    }

    override suspend fun swipe(targetUserId: String, decision: SwipeDecision): SwipeResult {
        require(candidates.any { it.user.id == targetUserId }) { "Candidate not found" }
        require(targetUserId != currentUser.id) { "Cannot swipe yourself" }

        swipes[targetUserId] = decision
        if (decision == SwipeDecision.PASS) {
            passedThisSession += targetUserId
            return SwipeResult(decision = decision, isMatch = false)
        }

        val mutualLike = targetUserId in candidatesWhoLikedCurrentUser()
        if (!mutualLike) return SwipeResult(decision = decision, isMatch = false)

        val candidate = candidates.first { it.user.id == targetUserId }
        val match = matches.firstOrNull { it.user.id == targetUserId }
            ?: DatingMatch("match-${matches.size + 1}", candidate.user, "Now").also(matches::add)
        return SwipeResult(decision = decision, isMatch = true, match = match)
    }

    override suspend fun getMatches(): List<DatingMatch> = matches.toList()

    override suspend fun resetPassedCandidates() {
        passedThisSession.clear()
    }

    private fun candidatesWhoLikedCurrentUser(): Set<String> =
        candidates.filter { it.likedYou }.mapTo(mutableSetOf()) { it.user.id }

    private fun calculateScore(candidate: DatingCandidate): Int {
        val commonInterests = profile.interests.intersect(candidate.interests.toSet()).size
        return commonInterests * 30 +
            (if (candidate.age in (profile.minAge ?: Int.MIN_VALUE)..(profile.maxAge ?: Int.MAX_VALUE)) 20 else 0) +
            (if (candidate.bio.isNotBlank()) 10 else 0) +
            (if (candidate.photoUrl != null) 10 else 0) +
            (if (candidate.likedYou) 40 else 0)
    }

    companion object {
        private fun defaultCandidates(currentUserId: String): List<DatingCandidate> = listOf(
            DatingCandidate(
                User("u2", "Emma Chen", "@emma", "EC"), 25,
                "Photographer and city explorer.", listOf("Travel", "Coffee"), 3.0,
                likedYou = false
            ),
            DatingCandidate(
                User("u3", "Amelia Wong", "@amelia", "AW"), 27,
                "Product designer who loves museums.", listOf("Design", "Travel"), 5.0
            ),
            DatingCandidate(
                User("u4", "Jenny Miller", "@jenny", "JM"), 24,
                "Coffee, books and weekend walks.", listOf("Coffee"), 2.0
            )
        ).filter { it.user.id != currentUserId }
    }
}
