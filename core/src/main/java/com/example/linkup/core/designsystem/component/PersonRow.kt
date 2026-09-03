package com.example.linkup.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.linkup.core.designsystem.motion.pressScale
import com.example.linkup.core.designsystem.motion.rememberShimmerBrush
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft

private val AvatarFallbackBrush = Brush.linearGradient(
    listOf(Color(0xFF9F67FF), Color(0xFFFF78A5))
)

/**
 * Circular avatar that shows a remote image when there is one and initials otherwise.
 *
 * Takes primitives rather than a domain type, so `core` stays independent of `data`.
 */
@Composable
fun CircleAvatar(
    avatarUrl: String?,
    initials: String,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(AvatarFallbackBrush),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl.isNullOrBlank()) {
            Text(
                initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 2.8f).sp
            )
        } else {
            AsyncImage(
                model = fadeInRequest(avatarUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Avatar for a group: up to two member avatars offset from each other, so a group
 * reads differently from a one-to-one chat at a glance.
 *
 * [members] is (avatarUrl, initials) per member, already trimmed to the ones to show.
 */
@Composable
fun GroupAvatar(
    members: List<Pair<String?, String>>,
    fallbackInitials: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    if (members.size < 2) {
        val single = members.firstOrNull()
        CircleAvatar(single?.first, single?.second ?: fallbackInitials, size, modifier)
        return
    }

    val faceSize = size * 0.66f
    Box(modifier.size(size)) {
        CircleAvatar(
            avatarUrl = members[1].first,
            initials = members[1].second,
            size = faceSize,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(faceSize + 3.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircleAvatar(members[0].first, members[0].second, faceSize)
        }
    }
}

/**
 * One person in a list.
 *
 * The trailing control is a slot rather than a fixed button: follower lists want a
 * follow toggle, friend lists want Confirm/Delete, and search wants Add friend. The
 * slot keeps all of that out of `core`, which has no idea what a friendship is.
 * [isMe] replaces the slot with a "You" label — you are never an action on yourself.
 */
@Composable
fun PersonRow(
    displayName: String,
    handle: String,
    initials: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    isMe: Boolean = false,
    onClick: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleAvatar(avatarUrl, initials)

        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(handle, color = LinkMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = LinkMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (isMe) {
            Text("You", color = LinkMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * Compact action button used in list rows.
 *
 * @param filled a solid purple call to action; outlined is the "already done" state.
 * @param danger red text for destructive choices such as declining a request.
 */
@Composable
fun ActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    danger: Boolean = false,
    isBusy: Boolean = false,
    enabled: Boolean = true
) {
    val content = when {
        danger -> Color(0xFFB3261E)
        filled -> Color.White
        else -> LinkPurple
    }
    val background = if (filled && !danger) LinkPurple else Color.White
    val actionInteraction = remember { MutableInteractionSource() }
    val border = when {
        danger -> LinkDivider
        filled -> LinkPurple
        else -> LinkDivider
    }

    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) background else background.copy(alpha = 0.5f))
            .border(1.dp, border, RoundedCornerShape(50))
            .pressScale(actionInteraction)
            .clickable(
                interactionSource = actionInteraction,
                indication = LocalIndication.current,
                enabled = enabled && !isBusy,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isBusy) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = content, modifier = Modifier.size(14.dp))
        } else {
            AnimatedContent(targetState = text, label = "actionLabel") { label ->
                Text(label, color = content, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

/** Compact follow toggle: filled when not yet following, outlined once you are. */
@Composable
fun FollowPill(
    isFollowing: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Colour eases between states so tapping Follow reads as one movement rather
    // than an instant repaint.
    val background by animateColorAsState(
        targetValue = if (isFollowing) Color.White else LinkPurple,
        animationSpec = tween(200),
        label = "followBackground"
    )
    val border by animateColorAsState(
        targetValue = if (isFollowing) LinkDivider else LinkPurple,
        animationSpec = tween(200),
        label = "followBorder"
    )
    val content by animateColorAsState(
        targetValue = if (isFollowing) LinkPurple else Color.White,
        animationSpec = tween(200),
        label = "followContent"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(50))
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = !isBusy,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = content,
                modifier = Modifier.size(14.dp)
            )
        } else {
            AnimatedContent(targetState = isFollowing, label = "followLabel") { following ->
                Text(
                    text = if (following) "Following" else "Follow",
                    color = content,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/** Placeholder rows used while a people list loads. */
@Composable
fun PersonRowSkeleton(count: Int = 8) {
    val shimmer = rememberShimmerBrush()
    Column(Modifier.fillMaxWidth()) {
        repeat(count) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(shimmer))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Box(
                        Modifier.fillMaxWidth(0.5f).size(14.dp)
                            .clip(RoundedCornerShape(4.dp)).background(shimmer)
                    )
                    Spacer(Modifier.size(6.dp))
                    Box(
                        Modifier.fillMaxWidth(0.3f).size(11.dp)
                            .clip(RoundedCornerShape(4.dp)).background(shimmer)
                    )
                }
            }
        }
    }
}

/**
 * Wraps a URL so Coil fades the decoded image in.
 *
 * Without this an avatar snaps from placeholder to photo the instant it decodes,
 * which reads as a flicker while a list scrolls.
 */
@Composable
internal fun fadeInRequest(url: String?): ImageRequest =
    ImageRequest.Builder(LocalContext.current)
        .data(url)
        .crossfade(true)
        .build()
