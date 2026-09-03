package com.example.linkup.feature.profile.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.linkup.core.designsystem.component.FriendControls
import com.example.linkup.core.designsystem.icon.LinkUpIcons
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft
import com.example.linkup.data.model.Profile
import com.example.linkup.feature.profile.CoverPhoto
import com.example.linkup.feature.profile.DetailChip
import com.example.linkup.feature.profile.DetailRow
import com.example.linkup.feature.profile.ProfileCard
import com.example.linkup.feature.profile.ProfileTabs
import com.example.linkup.feature.profile.RemoteAvatar
import com.example.linkup.feature.profile.StatColumn
import com.example.linkup.feature.profile.formatBirthdate
import com.example.linkup.feature.profile.formatJoinedDate
import com.example.linkup.feature.profile.friendActionState

private val TABS = listOf("Posts", "Reels", "Photos")

private val COVER_HEIGHT = 190.dp
private val AVATAR_SIZE = 96.dp
private val AVATAR_RING = 4.dp

/** How far the avatar hangs below the cover. */
private val AVATAR_OVERHANG = 52.dp

/**
 * Someone's profile.
 *
 * @param userId a user id or username; null shows the signed-in user's own profile.
 */
@Composable
fun ProfileScreen(
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    userId: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenFollowers: (String) -> Unit = {},
    onOpenFollowing: (String) -> Unit = {},
    onOpenFriends: (String) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) { viewModel.load(userId) }

    Box(modifier.fillMaxSize()) {
        when (val state = uiState) {
            // The skeleton and the error page keep a visible way back: without the
            // header, a profile that fails to load strands the user on a dead screen
            // with nothing but the system gesture to escape it.
            is ProfileUiState.Loading -> Column(Modifier.fillMaxSize()) {
                if (onBack != null) ScreenHeader(title = "Profile", onBack = onBack)
                ProfileSkeleton()
            }

            is ProfileUiState.Error -> Column(Modifier.fillMaxSize()) {
                if (onBack != null) ScreenHeader(title = "Profile", onBack = onBack)
                ProfileErrorState(
                    message = state.message,
                    onRetry = { viewModel.load(userId, force = true) }
                )
            }

            is ProfileUiState.Ready -> ProfileContent(
                state = state,
                onEdit = onEdit,
                onSettings = onSettings,
                onBack = onBack,
                onRefresh = viewModel::refresh,
                onToggleFollow = viewModel::toggleFollow,
                onDismissMessage = viewModel::consumeMessage,
                onOpenFollowers = onOpenFollowers,
                onOpenFollowing = onOpenFollowing,
                onOpenFriends = onOpenFriends,
                onFriendAction = FriendActions(
                    onAdd = viewModel::sendFriendRequest,
                    onCancel = viewModel::cancelFriendRequest,
                    onAccept = viewModel::acceptFriendRequest,
                    onDecline = viewModel::declineFriendRequest,
                    onUnfriend = viewModel::unfriend
                )
            )
        }
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState.Ready,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onToggleFollow: () -> Unit,
    onDismissMessage: () -> Unit,
    onOpenFollowers: (String) -> Unit,
    onOpenFollowing: (String) -> Unit,
    onOpenFriends: (String) -> Unit,
    onFriendAction: FriendActions
) {
    val profile = state.profile
    var tab by remember { mutableStateOf(TABS.first()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

        ProfileHeader(
            profile = profile,
            isRefreshing = state.isRefreshing,
            onBack = onBack,
            onRefresh = onRefresh,
            onSettings = onSettings
        )

        Column(Modifier.padding(horizontal = 20.dp)) {

            val message = state.message
            if (message != null) {
                InlineBanner(text = message, onDismiss = onDismissMessage)
                Spacer(Modifier.height(12.dp))
            }

            Text(profile.displayName, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.handle, color = LinkMuted, fontSize = 14.sp)
                if (profile.isFollowedBy) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(LinkDivider)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Follows you", color = LinkMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!profile.isMe && profile.mutualFriendCount > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (profile.mutualFriendCount == 1) {
                        "1 mutual friend"
                    } else {
                        "${profile.mutualFriendCount} mutual friends"
                    },
                    color = LinkPurple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val bio = profile.bio
            if (!bio.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(bio, fontSize = 14.sp, lineHeight = 20.sp)
            }

            ProfileMetaRow(profile)

            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                StatColumn(profile.postCount, "Posts", Modifier.weight(1f))
                StatColumn(
                    value = profile.friendCount,
                    label = "Friends",
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenFriends(profile.id) }
                )
                StatColumn(
                    value = profile.followerCount,
                    label = "Followers",
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenFollowers(profile.id) }
                )
                StatColumn(
                    value = profile.followingCount,
                    label = "Following",
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenFollowing(profile.id) }
                )
            }

            Spacer(Modifier.height(4.dp))
            ProfileActions(
                profile = profile,
                followInFlight = state.followInFlight,
                friendActionInFlight = state.friendActionInFlight,
                onEdit = onEdit,
                onSettings = onSettings,
                onToggleFollow = onToggleFollow,
                onFriendAction = onFriendAction
            )

            if (profile.isMe && profile.hasContactDetails) {
                Spacer(Modifier.height(20.dp))
                PrivateDetailsCard(profile)
            }

            Spacer(Modifier.height(20.dp))
        }

        HorizontalDivider(color = LinkDivider)
        ProfileTabs(tabs = TABS, selected = tab, onSelect = { tab = it })
        HorizontalDivider(color = LinkDivider)

        TabPlaceholder(tab = tab, isMe = profile.isMe, name = profile.displayName)
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Cover, overlaid controls and the avatar straddling the cover edge.
 *
 * The cover carries bottom padding equal to the overhang, which reserves the space
 * the avatar hangs into — so the name below is never overlapped.
 */
@Composable
private fun ProfileHeader(
    profile: Profile,
    isRefreshing: Boolean,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        CoverPhoto(
            url = profile.coverUrl,
            height = COVER_HEIGHT,
            modifier = Modifier.padding(bottom = AVATAR_OVERHANG)
        )

        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                OverlayIcon(LinkUpIcons.ChevronLeft, "Back", onBack)
            }
            Spacer(Modifier.weight(1f))
            if (isRefreshing) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                OverlayIcon(LinkUpIcons.Refresh, "Refresh", onRefresh)
            }
            if (profile.isMe) {
                Spacer(Modifier.width(8.dp))
                OverlayIcon(LinkUpIcons.Settings, "Settings", onSettings)
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp)
        ) {
            RemoteAvatar(
                url = profile.avatarUrl,
                initials = profile.initials,
                size = AVATAR_SIZE,
                ringWidth = AVATAR_RING
            )
        }
    }
}

@Composable
private fun ProfileMetaRow(profile: Profile) {
    val joined = formatJoinedDate(profile.joinedAt)
    val location = profile.location?.takeIf { it.isNotBlank() }
    val website = profile.websiteLabel?.takeIf { it.isNotBlank() }
    if (location == null && website == null && joined == null) return

    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        location?.let { DetailChip(LinkUpIcons.Location, it) }
        website?.let { DetailChip(LinkUpIcons.Link, it, tint = LinkPurple) }
        joined?.let { DetailChip(LinkUpIcons.Calendar, it) }
    }
}

@Composable
private fun PrivateDetailsCard(profile: Profile) {
    ProfileCard {
        Text("Your details", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        profile.email?.takeIf { it.isNotBlank() }?.let { DetailRow(LinkUpIcons.Mail, "Email", it) }
        profile.phone?.takeIf { it.isNotBlank() }?.let { DetailRow(LinkUpIcons.Phone, "Phone", it) }
        profile.location?.takeIf { it.isNotBlank() }?.let { DetailRow(LinkUpIcons.Location, "Location", it) }
        profile.website?.takeIf { it.isNotBlank() }?.let { DetailRow(LinkUpIcons.Link, "Website", it) }
        profile.birthdate?.takeIf { it.isNotBlank() }?.let { raw ->
            formatBirthdate(raw)?.let { DetailRow(LinkUpIcons.Star, "Birthday", it) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Only you can see this section.", color = LinkMuted, fontSize = 11.sp)
    }
}

/** The five friend callbacks, grouped so the action row keeps a readable signature. */
data class FriendActions(
    val onAdd: () -> Unit,
    val onCancel: () -> Unit,
    val onAccept: () -> Unit,
    val onDecline: () -> Unit,
    val onUnfriend: () -> Unit
)

@Composable
private fun ProfileActions(
    profile: Profile,
    followInFlight: Boolean,
    friendActionInFlight: Boolean,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onToggleFollow: () -> Unit,
    onFriendAction: FriendActions
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (profile.isMe) {
            Button(
                onClick = onEdit,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LinkPurple)
            ) {
                Text("Edit profile", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onSettings,
                modifier = Modifier.height(44.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(LinkUpIcons.Settings, "Settings", modifier = Modifier.size(18.dp))
            }
        } else {
            // Friendship is the primary relationship, so it leads; following is
            // secondary and independent — you can follow someone without friending them.
            FriendControls(
                state = profile.friendship.friendActionState(),
                isBusy = friendActionInFlight,
                compact = false,
                onAdd = onFriendAction.onAdd,
                onCancel = onFriendAction.onCancel,
                onAccept = onFriendAction.onAccept,
                onDecline = onFriendAction.onDecline,
                onUnfriend = onFriendAction.onUnfriend
            )
            Button(
                onClick = onToggleFollow,
                enabled = !followInFlight,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (profile.isFollowing) LinkPurpleSoft else LinkPurple,
                    contentColor = if (profile.isFollowing) LinkPurple else Color.White
                )
            ) {
                Text(if (profile.isFollowing) "Following" else "Follow", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TabPlaceholder(tab: String, isMe: Boolean, name: String) {
    val message = when (tab) {
        "Posts" ->
            if (isMe) "Your posts will appear here once the feed API is connected."
            else "$name hasn't posted yet."
        "Reels" ->
            if (isMe) "Reels you upload will show up here."
            else "$name hasn't shared a reel yet."
        else ->
            if (isMe) "Photos from your posts will collect here."
            else "No photos from $name yet."
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(LinkPurpleSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(LinkUpIcons.Diamond, null, tint = LinkPurple, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("Nothing here yet", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text(message, color = LinkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** Circular control floating on the cover photo, legible against any image. */
@Composable
private fun OverlayIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun InlineBanner(text: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFDECEF))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = Color(0xFFB3261E), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Icon(
            imageVector = LinkUpIcons.Close,
            contentDescription = "Dismiss",
            tint = Color(0xFFB3261E),
            modifier = Modifier.clickable(onClick = onDismiss).padding(start = 8.dp).size(16.dp)
        )
    }
}

/** Placeholder blocks matching the real layout, so loading does not shift content. */
@Composable
private fun ProfileSkeleton() {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(COVER_HEIGHT)
                    .padding(bottom = AVATAR_OVERHANG)
                    .background(LinkDivider)
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp)
                    .size(AVATAR_SIZE + AVATAR_RING * 2)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(AVATAR_SIZE).clip(CircleShape).background(LinkDivider))
            }
        }
        Column(Modifier.padding(20.dp)) {
            SkeletonBar(180.dp, 22.dp)
            Spacer(Modifier.height(10.dp))
            SkeletonBar(110.dp, 14.dp)
            Spacer(Modifier.height(18.dp))
            SkeletonBar(260.dp, 14.dp)
            Spacer(Modifier.height(8.dp))
            SkeletonBar(200.dp, 14.dp)
            Spacer(Modifier.height(24.dp))
            SkeletonBar(320.dp, 44.dp)
        }
    }
}

@Composable
private fun SkeletonBar(width: Dp, height: Dp) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(LinkDivider)
    )
}

@Composable
private fun ProfileErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(LinkPurpleSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(LinkUpIcons.Info, null, tint = LinkPurple, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Couldn't load this profile", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text(message, color = LinkMuted, fontSize = 13.sp, textAlign = TextAlign.Center)

        // A missing account is almost always placeholder content rather than a
        // failure: Feed and Reels still ship sample people with no real accounts
        // behind them. Say so, and point at the screens that do open real profiles.
        if (message.contains("not found", ignoreCase = true)) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Posts in Feed and Reels are still sample content, so their " +
                    "authors aren't real accounts yet.",
                color = LinkMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Search, Friends, Chats and Notifications open real profiles.",
                color = LinkPurple,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LinkPurple)
        ) {
            Text("Try again", fontWeight = FontWeight.Bold)
        }
    }
}
