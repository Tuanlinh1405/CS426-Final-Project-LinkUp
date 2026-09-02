package com.example.linkup.core.designsystem.component

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
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
            .clickable(enabled = enabled && !isBusy, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isBusy) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = content, modifier = Modifier.size(14.dp))
        } else {
            Text(text, color = content, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
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
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (isFollowing) Color.White else LinkPurple)
            .border(
                width = 1.dp,
                color = if (isFollowing) LinkDivider else LinkPurple,
                shape = RoundedCornerShape(50)
            )
            .clickable(enabled = !isBusy, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = if (isFollowing) LinkPurple else Color.White,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Text(
                text = if (isFollowing) "Following" else "Follow",
                color = if (isFollowing) LinkPurple else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

/** Placeholder rows used while a people list loads. */
@Composable
fun PersonRowSkeleton(count: Int = 8) {
    Column(Modifier.fillMaxWidth()) {
        repeat(count) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(LinkPurpleSoft))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Box(
                        Modifier.fillMaxWidth(0.5f).size(14.dp)
                            .clip(RoundedCornerShape(4.dp)).background(LinkPurpleSoft)
                    )
                    Spacer(Modifier.size(6.dp))
                    Box(
                        Modifier.fillMaxWidth(0.3f).size(11.dp)
                            .clip(RoundedCornerShape(4.dp)).background(LinkPurpleSoft)
                    )
                }
            }
        }
    }
}
