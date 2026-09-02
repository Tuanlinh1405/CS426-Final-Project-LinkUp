package com.example.linkup.feature.profile.edit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.linkup.data.local.media.DeviceImageReader
import com.example.linkup.data.model.PickedImage
import com.example.linkup.data.model.Profile
import com.example.linkup.data.model.ProfileException
import com.example.linkup.data.repository.ProfileRepository
import com.example.linkup.data.validation.ProfileFormRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** Identifies a form field for both state updates and error lookup. */
enum class ProfileField(val key: String) {
    FULL_NAME("fullName"),
    USERNAME("username"),
    EMAIL("email"),
    PHONE("phone"),
    BIO("bio"),
    LOCATION("location"),
    WEBSITE("website"),
    BIRTHDATE("birthdate"),
    GENDER("gender")
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val imageReader: DeviceImageReader
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private var loadedOnce = false

    fun load(force: Boolean = false) {
        if (loadedOnce && !force) return
        loadedOnce = true
        _uiState.update { it.copy(isLoading = true, loadError = null) }

        viewModelScope.launch {
            profileRepository.getMyProfile()
                .onSuccess { profile -> applyProfile(profile, resetForm = true) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadError = error.message ?: "Could not load your profile"
                        )
                    }
                }
        }
    }

    fun onFieldChange(field: ProfileField, value: String) {
        _uiState.update { state ->
            val form = state.form.updated(field, value)
            state.copy(
                form = form,
                touched = state.touched + field.key,
                errors = validate(form),
                // A local edit invalidates whatever the server last complained about.
                banner = null,
                savedAt = null
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return

        val errors = validate(state.form)
        if (errors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    saveAttempted = true,
                    errors = errors,
                    banner = Banner("Please fix the highlighted fields", isError = true)
                )
            }
            return
        }
        if (!state.hasChanges) {
            _uiState.update { it.copy(banner = Banner("Nothing to save yet", isError = false)) }
            return
        }

        _uiState.update { it.copy(isSaving = true, saveAttempted = true, banner = null) }

        viewModelScope.launch {
            profileRepository.updateProfile(state.form.diffAgainst(state.original))
                .onSuccess { profile ->
                    applyProfile(profile, resetForm = true)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            banner = Banner("Profile updated", isError = false),
                            savedAt = System.currentTimeMillis()
                        )
                    }
                }
                .onFailure { error -> applyFailure(error, "Could not save your changes") }
        }
    }

    fun onAvatarPicked(uri: Uri) = uploadImage(uri, isAvatar = true)

    fun onCoverPicked(uri: Uri) = uploadImage(uri, isAvatar = false)

    fun removeAvatar() {
        if (_uiState.value.avatarBusy) return
        _uiState.update { it.copy(avatarBusy = true, banner = null) }
        viewModelScope.launch {
            profileRepository.removeAvatar()
                .onSuccess { profile ->
                    applyProfile(profile, resetForm = false)
                    _uiState.update {
                        it.copy(avatarBusy = false, banner = Banner("Photo removed", isError = false))
                    }
                }
                .onFailure { error ->
                    applyFailure(error, "Could not remove your photo")
                    _uiState.update { it.copy(avatarBusy = false) }
                }
        }
    }

    fun removeCover() {
        if (_uiState.value.coverBusy) return
        _uiState.update { it.copy(coverBusy = true, banner = null) }
        viewModelScope.launch {
            profileRepository.removeCover()
                .onSuccess { profile ->
                    applyProfile(profile, resetForm = false)
                    _uiState.update {
                        it.copy(coverBusy = false, banner = Banner("Cover removed", isError = false))
                    }
                }
                .onFailure { error ->
                    applyFailure(error, "Could not remove your cover")
                    _uiState.update { it.copy(coverBusy = false) }
                }
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(banner = null) }
    }

    /**
     * Clears the save marker once the screen has navigated away.
     *
     * Without this the flag would still be set the next time the screen opens — this
     * view model is activity-scoped while the app routes with `AppNavigator` rather
     * than a NavHost — and the screen would bounce straight back out.
     */
    fun consumeSaved() {
        _uiState.update { it.copy(savedAt = null) }
    }

    /** Drops unsaved edits, so reopening the screen does not resurrect them. */
    fun discardChanges() {
        _uiState.update { state ->
            state.copy(
                form = state.original,
                errors = validate(state.original),
                touched = emptySet(),
                saveAttempted = false,
                banner = null
            )
        }
    }

    // ---- internals -------------------------------------------------------

    private fun uploadImage(uri: Uri, isAvatar: Boolean) {
        val state = _uiState.value
        if (if (isAvatar) state.avatarBusy else state.coverBusy) return

        _uiState.update {
            if (isAvatar) it.copy(avatarBusy = true, banner = null)
            else it.copy(coverBusy = true, banner = null)
        }

        viewModelScope.launch {
            val maxDimension = if (isAvatar) {
                DeviceImageReader.AVATAR_MAX_DIMENSION
            } else {
                DeviceImageReader.COVER_MAX_DIMENSION
            }
            val fileName = if (isAvatar) "avatar.jpg" else "cover.jpg"

            val image: PickedImage? = runCatching {
                imageReader.read(uri, maxDimension, fileName)
            }.getOrNull()

            if (image == null) {
                _uiState.update {
                    val cleared = if (isAvatar) it.copy(avatarBusy = false) else it.copy(coverBusy = false)
                    cleared.copy(banner = Banner("That image could not be read", isError = true))
                }
                return@launch
            }

            val result = if (isAvatar) {
                profileRepository.uploadAvatar(image)
            } else {
                profileRepository.uploadCover(image)
            }

            result
                .onSuccess { profile ->
                    applyProfile(profile, resetForm = false)
                    _uiState.update {
                        val cleared = if (isAvatar) it.copy(avatarBusy = false) else it.copy(coverBusy = false)
                        cleared.copy(
                            banner = Banner(
                                if (isAvatar) "Profile photo updated" else "Cover photo updated",
                                isError = false
                            )
                        )
                    }
                }
                .onFailure { error ->
                    applyFailure(error, "Could not upload that image")
                    _uiState.update {
                        if (isAvatar) it.copy(avatarBusy = false) else it.copy(coverBusy = false)
                    }
                }
        }
    }

    /**
     * Takes the server's version of the profile as the new baseline.
     *
     * @param resetForm true after a load or a save, when the form should match the
     *   server exactly. False after a photo upload, which must not discard text the
     *   user is still typing — only the baseline moves.
     */
    private fun applyProfile(profile: Profile, resetForm: Boolean) {
        val serverForm = ProfileForm.from(profile)
        _uiState.update { state ->
            val form = if (resetForm || state.isLoading) serverForm else state.form
            state.copy(
                isLoading = false,
                loadError = null,
                profile = profile,
                form = form,
                original = serverForm,
                errors = validate(form),
                touched = if (resetForm) emptySet() else state.touched,
                saveAttempted = if (resetForm) false else state.saveAttempted
            )
        }
    }

    private fun applyFailure(error: Throwable, fallback: String) {
        val serverFieldErrors = (error as? ProfileException)?.fieldErrors.orEmpty()
        _uiState.update { state ->
            state.copy(
                isSaving = false,
                errors = state.errors + serverFieldErrors,
                saveAttempted = state.saveAttempted || serverFieldErrors.isNotEmpty(),
                banner = Banner(error.message ?: fallback, isError = true)
            )
        }
    }

    private fun validate(form: ProfileForm): Map<String, String> {
        val today = todayIso()
        return buildMap {
            ProfileFormRules.fullName(form.fullName)?.let { put(ProfileField.FULL_NAME.key, it) }
            ProfileFormRules.username(form.username)?.let { put(ProfileField.USERNAME.key, it) }
            ProfileFormRules.email(form.email)?.let { put(ProfileField.EMAIL.key, it) }
            ProfileFormRules.phone(form.phone)?.let { put(ProfileField.PHONE.key, it) }
            ProfileFormRules.bio(form.bio)?.let { put(ProfileField.BIO.key, it) }
            ProfileFormRules.location(form.location)?.let { put(ProfileField.LOCATION.key, it) }
            ProfileFormRules.website(form.website)?.let { put(ProfileField.WEBSITE.key, it) }
            ProfileFormRules.birthdate(form.birthdate, today)?.let { put(ProfileField.BIRTHDATE.key, it) }
        }
    }

    /** `yyyy-MM-dd` without java.time, which needs API 26 or desugaring. */
    private fun todayIso(): String {
        val calendar = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
}

/** Returns a copy of the form with one field replaced. */
private fun ProfileForm.updated(field: ProfileField, value: String): ProfileForm = when (field) {
    ProfileField.FULL_NAME -> copy(fullName = value)
    ProfileField.USERNAME -> copy(username = value)
    ProfileField.EMAIL -> copy(email = value)
    ProfileField.PHONE -> copy(phone = value)
    ProfileField.BIO -> copy(bio = value)
    ProfileField.LOCATION -> copy(location = value)
    ProfileField.WEBSITE -> copy(website = value)
    ProfileField.BIRTHDATE -> copy(birthdate = value)
    ProfileField.GENDER -> copy(gender = value)
}
