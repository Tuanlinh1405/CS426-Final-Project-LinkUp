package com.example.linkup.data.repository

import com.example.linkup.data.model.PickedImage
import com.example.linkup.data.model.Profile
import com.example.linkup.data.model.ProfileUpdate

interface ProfileRepository {

    /** The signed-in user's own profile, including contact details. */
    suspend fun getMyProfile(): Result<Profile>

    /** Another user's profile by id or username. Contact details are omitted. */
    suspend fun getProfile(idOrUsername: String): Result<Profile>

    /** Sends only the changed fields; returns the refreshed profile. */
    suspend fun updateProfile(update: ProfileUpdate): Result<Profile>

    suspend fun uploadAvatar(image: PickedImage): Result<Profile>

    suspend fun uploadCover(image: PickedImage): Result<Profile>

    suspend fun removeAvatar(): Result<Profile>

    suspend fun removeCover(): Result<Profile>

    /** @return the resulting follow state. */
    suspend fun setFollowing(userId: String, follow: Boolean): Result<Boolean>
}
