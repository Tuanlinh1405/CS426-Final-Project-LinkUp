package com.example.linkup.feature.profile.edit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.linkup.core.designsystem.icon.LinkUpIcons
import com.example.linkup.core.designsystem.component.ChoiceChip
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.data.model.Profile
import com.example.linkup.data.validation.ProfileFormRules
import com.example.linkup.feature.profile.CoverPhoto
import com.example.linkup.feature.profile.RemoteAvatar

private val GENDER_OPTIONS = listOf(
    "" to "Not set",
    "MALE" to "Male",
    "FEMALE" to "Female",
    "OTHER" to "Other",
    "PREFER_NOT_TO_SAY" to "Prefer not to say"
)

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    // Leaving after a successful save lands back on the profile, which refreshes itself.
    LaunchedEffect(state.savedAt) {
        if (state.savedAt != null) {
            viewModel.consumeSaved()
            onSaved()
        }
    }

    val requestBack = {
        if (state.hasChanges) showDiscardDialog = true else onBack()
    }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let(viewModel::onAvatarPicked) }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let(viewModel::onCoverPicked) }

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Column(modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = "Edit profile",
            onBack = requestBack,
            action = if (state.isSaving) "Saving…" else "Save",
            onAction = viewModel::save
        )

        when {
            state.isLoading -> LoadingBlock()

            state.loadError != null -> LoadErrorBlock(
                message = state.loadError.orEmpty(),
                onRetry = { viewModel.load(force = true) }
            )

            else -> EditForm(
                state = state,
                onChangeAvatar = { avatarPicker.launch(imageOnly) },
                onChangeCover = { coverPicker.launch(imageOnly) },
                onRemoveAvatar = viewModel::removeAvatar,
                onRemoveCover = viewModel::removeCover,
                onFieldChange = viewModel::onFieldChange,
                onDismissBanner = viewModel::dismissBanner,
                onSave = viewModel::save
            )
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have edits that haven't been saved yet.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.discardChanges()
                        onBack()
                    }
                ) {
                    Text("Discard", color = Color(0xFFB3261E))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            }
        )
    }
}

@Composable
private fun EditForm(
    state: EditProfileUiState,
    onChangeAvatar: () -> Unit,
    onChangeCover: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onRemoveCover: () -> Unit,
    onFieldChange: (ProfileField, String) -> Unit,
    onDismissBanner: () -> Unit,
    onSave: () -> Unit
) {
    val profile = state.profile
    val form = state.form

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        PhotoHeader(
            profile = profile,
            initials = profile?.initials ?: form.username.take(1).uppercase(),
            avatarBusy = state.avatarBusy,
            coverBusy = state.coverBusy,
            onChangeAvatar = onChangeAvatar,
            onChangeCover = onChangeCover,
            onRemoveAvatar = onRemoveAvatar,
            onRemoveCover = onRemoveCover
        )

        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {

            state.banner?.let { banner ->
                FeedbackBanner(banner, onDismissBanner)
                Spacer(Modifier.height(16.dp))
            }

            SectionTitle("About you")
            EditField(
                value = form.fullName,
                onValueChange = { onFieldChange(ProfileField.FULL_NAME, it) },
                label = "Full name",
                placeholder = "How your name appears to others",
                error = state.errorFor(ProfileField.FULL_NAME.key),
                counter = form.fullName.length to ProfileFormRules.MAX_FULL_NAME
            )
            EditField(
                value = form.username,
                onValueChange = { onFieldChange(ProfileField.USERNAME, it) },
                label = "Username",
                placeholder = "sarah.j",
                prefix = "@",
                error = state.errorFor(ProfileField.USERNAME.key),
                supporting = "People can find you at linkup.dev/${form.username.trim()}"
            )
            EditField(
                value = form.bio,
                onValueChange = { onFieldChange(ProfileField.BIO, it) },
                label = "Bio",
                placeholder = "Say something about yourself",
                error = state.errorFor(ProfileField.BIO.key),
                singleLine = false,
                fixedHeight = 108.dp,
                counter = form.bio.length to ProfileFormRules.MAX_BIO
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle("Contact")
            Text(
                "Only you can see your email and phone number.",
                color = LinkMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            EditField(
                value = form.email,
                onValueChange = { onFieldChange(ProfileField.EMAIL, it) },
                label = "Email",
                placeholder = "you@example.com",
                error = state.errorFor(ProfileField.EMAIL.key),
                keyboardType = KeyboardType.Email
            )
            EditField(
                value = form.phone,
                onValueChange = { onFieldChange(ProfileField.PHONE, it) },
                label = "Phone number",
                placeholder = "+84 912 345 678",
                error = state.errorFor(ProfileField.PHONE.key),
                keyboardType = KeyboardType.Phone,
                supporting = "Optional — leave blank to remove it"
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle("More about you")
            EditField(
                value = form.location,
                onValueChange = { onFieldChange(ProfileField.LOCATION, it) },
                label = "Location",
                placeholder = "Ho Chi Minh City, Vietnam",
                error = state.errorFor(ProfileField.LOCATION.key)
            )
            EditField(
                value = form.website,
                onValueChange = { onFieldChange(ProfileField.WEBSITE, it) },
                label = "Website",
                placeholder = "linkup.dev",
                error = state.errorFor(ProfileField.WEBSITE.key),
                keyboardType = KeyboardType.Uri
            )
            EditField(
                value = form.birthdate,
                onValueChange = { onFieldChange(ProfileField.BIRTHDATE, it) },
                label = "Birthday",
                placeholder = "YYYY-MM-DD",
                error = state.errorFor(ProfileField.BIRTHDATE.key),
                keyboardType = KeyboardType.Number,
                supporting = "Optional — used to confirm you're old enough"
            )

            Spacer(Modifier.height(6.dp))
            Text("Gender", fontSize = 13.sp, color = LinkMuted)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GENDER_OPTIONS.take(3).forEach { (value, label) ->
                    ChoiceChip(label, selected = form.gender == value) {
                        onFieldChange(ProfileField.GENDER, value)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GENDER_OPTIONS.drop(3).forEach { (value, label) ->
                    ChoiceChip(label, selected = form.gender == value) {
                        onFieldChange(ProfileField.GENDER, value)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LinkPurple)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        if (state.hasChanges) "Save changes" else "No changes yet",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Cover and avatar, each with its own change / remove affordances. */
@Composable
private fun PhotoHeader(
    profile: Profile?,
    initials: String,
    avatarBusy: Boolean,
    coverBusy: Boolean,
    onChangeAvatar: () -> Unit,
    onChangeCover: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onRemoveCover: () -> Unit
) {
    Column {
        Box(Modifier.fillMaxWidth()) {
            CoverPhoto(
                url = profile?.coverUrl,
                height = 170.dp,
                isBusy = coverBusy,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            Row(
                Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PhotoActionPill("Change cover", onChangeCover, enabled = !coverBusy)
                if (!profile?.coverUrl.isNullOrBlank()) {
                    PhotoActionPill("Remove", onRemoveCover, enabled = !coverBusy)
                }
            }

            Box(Modifier.align(Alignment.BottomStart).padding(start = 20.dp)) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    RemoteAvatar(
                        url = profile?.avatarUrl,
                        initials = initials,
                        size = 92.dp,
                        isBusy = avatarBusy
                    )
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(LinkPurple)
                            .clickable(enabled = !avatarBusy, onClick = onChangeAvatar),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(LinkUpIcons.Camera, "Change photo", tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Change photo",
                color = LinkPurple,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = !avatarBusy, onClick = onChangeAvatar)
            )
            if (!profile?.avatarUrl.isNullOrBlank()) {
                Text(
                    "Remove photo",
                    color = LinkMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(enabled = !avatarBusy, onClick = onRemoveAvatar)
                )
            }
        }
    }
}

@Composable
private fun PhotoActionPill(text: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = if (enabled) 0.38f else 0.2f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Text field with inline validation, an optional prefix and an optional counter. */
@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    prefix: String? = null,
    error: String? = null,
    supporting: String? = null,
    counter: Pair<Int, Int>? = null,
    singleLine: Boolean = true,
    fixedHeight: Dp = 0.dp,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fixedHeight > 0.dp) Modifier.height(fixedHeight) else Modifier),
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            leadingIcon = prefix?.let { { Text(it, color = LinkMuted) } },
            isError = error != null,
            singleLine = singleLine,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val helper = error ?: supporting
            if (helper != null) {
                Text(
                    text = helper,
                    color = if (error != null) Color(0xFFB3261E) else LinkMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (counter != null) {
                val (used, limit) = counter
                Text(
                    "$used/$limit",
                    color = if (used > limit) Color(0xFFB3261E) else LinkMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun FeedbackBanner(banner: Banner, onDismiss: () -> Unit) {
    val background = if (banner.isError) Color(0xFFFDECEF) else Color(0xFFE9F7EF)
    val foreground = if (banner.isError) Color(0xFFB3261E) else Color(0xFF1B7A43)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(banner.message, color = foreground, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            "✕",
            color = foreground,
            fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onDismiss).padding(start = 8.dp)
        )
    }
}

@Composable
private fun LoadingBlock() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LinkPurple)
    }
}

@Composable
private fun LoadErrorBlock(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(LinkDivider),
            contentAlignment = Alignment.Center
        ) {
            Icon(LinkUpIcons.Info, null, tint = LinkMuted, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Couldn't open your profile", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(message, color = LinkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LinkPurple)
        ) {
            Text("Try again", fontWeight = FontWeight.Bold)
        }
    }
}
