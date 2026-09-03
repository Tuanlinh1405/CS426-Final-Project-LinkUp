package com.example.linkup.feature.profile.edit

import com.example.linkup.data.model.Profile
import com.example.linkup.data.model.ProfileUpdate

/** Editable text fields, kept as strings so the form is the single source of truth. */
data class ProfileForm(
    val fullName: String = "",
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val bio: String = "",
    val location: String = "",
    val website: String = "",
    val birthdate: String = "",
    val gender: String = ""
) {
    companion object {
        fun from(profile: Profile) = ProfileForm(
            fullName = profile.fullName.orEmpty(),
            username = profile.username,
            email = profile.email.orEmpty(),
            phone = profile.phone.orEmpty(),
            bio = profile.bio.orEmpty(),
            location = profile.location.orEmpty(),
            website = profile.website.orEmpty(),
            birthdate = profile.birthdate.orEmpty(),
            gender = profile.gender.orEmpty()
        )
    }

    /**
     * Builds a request containing only what actually changed, so an untouched
     * field can never be overwritten by a stale value.
     */
    fun diffAgainst(original: ProfileForm): ProfileUpdate = ProfileUpdate(
        fullName = fullName.takeIf { it.trim() != original.fullName.trim() }?.trim(),
        username = username.takeIf { it.trim() != original.username.trim() }?.trim(),
        email = email.takeIf { it.trim() != original.email.trim() }?.trim(),
        phone = phone.takeIf { it.trim() != original.phone.trim() }?.trim(),
        bio = bio.takeIf { it.trim() != original.bio.trim() }?.trim(),
        location = location.takeIf { it.trim() != original.location.trim() }?.trim(),
        website = website.takeIf { it.trim() != original.website.trim() }?.trim(),
        birthdate = birthdate.takeIf { it.trim() != original.birthdate.trim() }?.trim(),
        gender = gender.takeIf { it.trim() != original.gender.trim() }?.trim()
    )
}

/** Inline feedback strip at the top of the form. */
data class Banner(val message: String, val isError: Boolean)

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val profile: Profile? = null,
    val form: ProfileForm = ProfileForm(),
    val original: ProfileForm = ProfileForm(),
    /** Field name to message, from local rules and from the server. */
    val errors: Map<String, String> = emptyMap(),
    /** Fields the user has edited; errors stay hidden until then. */
    val touched: Set<String> = emptySet(),
    val saveAttempted: Boolean = false,
    val isSaving: Boolean = false,
    val avatarBusy: Boolean = false,
    val coverBusy: Boolean = false,
    val banner: Banner? = null,
    /** Set once the save succeeded so the screen can navigate back. */
    val savedAt: Long? = null
) {
    val hasChanges: Boolean get() = form != original

    val isValid: Boolean get() = errors.isEmpty()

    val canSave: Boolean get() = hasChanges && isValid && !isSaving && !isLoading

    /** An error is only shown once its field has been touched, or after a save attempt. */
    fun errorFor(field: String): String? =
        if (saveAttempted || field in touched) errors[field] else null
}
