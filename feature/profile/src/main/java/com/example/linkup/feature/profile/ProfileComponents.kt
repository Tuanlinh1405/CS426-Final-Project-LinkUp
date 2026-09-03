package com.example.linkup.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.linkup.core.designsystem.component.FriendActionState
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft
import com.example.linkup.data.model.FriendshipStatus

/** Gradient used whenever a user has not set a cover photo. */
internal val CoverFallbackBrush = Brush.linearGradient(
    listOf(Color(0xFF44187D), Color(0xFF7C3AED), Color(0xFFE73C91))
)

private val AvatarFallbackBrush = Brush.linearGradient(
    listOf(Color(0xFF9F67FF), Color(0xFFFF78A5))
)

/**
 * Circular avatar that shows the remote image when there is one and falls back to
 * initials on a gradient, so the header never renders as an empty hole.
 */
@Composable
fun RemoteAvatar(
    url: String?,
    initials: String,
    size: Dp,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 4.dp,
    ringColor: Color = Color.White,
    isBusy: Boolean = false
) {
    Box(
        modifier = modifier
            .size(size + ringWidth * 2)
            .clip(CircleShape)
            .background(ringColor),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(AvatarFallbackBrush),
            contentAlignment = Alignment.Center
        ) {
            if (url.isNullOrBlank()) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = (size.value / 2.6f).sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                AsyncImage(
                    model = fadeInRequest(url),
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isBusy) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** Cover photo with a gradient fallback and a bottom scrim so overlaid text stays readable. */
@Composable
fun CoverPhoto(
    url: String?,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    isBusy: Boolean = false
) {
    Box(modifier.fillMaxWidth().height(height)) {
        if (url.isNullOrBlank()) {
            Box(Modifier.fillMaxSize().background(CoverFallbackBrush))
        } else {
            AsyncImage(
                model = fadeInRequest(url),
                contentDescription = "Cover photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(CoverFallbackBrush)
            )
        }

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.28f)
                )
            )
        )

        if (isBusy) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

/** One figure in the followers / following / posts row. */
@Composable
fun StatColumn(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(formatCount(value), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(label, color = LinkMuted, fontSize = 12.sp)
    }
}

/** Small pill used for location, link and join date. */
@Composable
fun DetailChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = LinkMuted,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = tint,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Bordered card used for the private contact block and the tab content. */
@Composable
fun ProfileCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, LinkDivider, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}

/** A labelled line inside [ProfileCard]. */
@Composable
fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(LinkPurpleSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = LinkPurple, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = LinkMuted, fontSize = 11.sp)
            Text(value, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Underlined tab row; avoids the experimental Material 3 tab APIs. */
@Composable
fun ProfileTabs(
    tabs: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tab,
                    color = if (isSelected) LinkPurple else LinkMuted,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .fillMaxWidth(if (isSelected) 0.55f else 0f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) LinkPurple else Color.Transparent)
                )
            }
        }
    }
}

/** 1.2K / 12.4K / 3.1M, so long numbers never break the stats row. */
fun formatCount(value: Int): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> {
        val tenths = value / 100
        if (tenths % 10 == 0) "${tenths / 10}K" else "${tenths / 10}.${tenths % 10}K"
    }
    else -> {
        val tenths = value / 100_000
        if (tenths % 10 == 0) "${tenths / 10}M" else "${tenths / 10}.${tenths % 10}M"
    }
}

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/**
 * Formats the ISO timestamp the backend sends into "Joined Sep 2026".
 *
 * Parsed by hand rather than with java.time, which needs API 26 or desugaring
 * while this module targets minSdk 24.
 */
fun formatJoinedDate(iso: String): String? {
    if (iso.length < 7) return null
    val year = iso.substring(0, 4).toIntOrNull() ?: return null
    val month = iso.substring(5, 7).toIntOrNull() ?: return null
    if (month !in 1..12) return null
    return "Joined ${MONTHS[month - 1]} $year"
}

/** Formats `yyyy-MM-dd` as "29 Apr 2006" for the read-only birthday line. */
fun formatBirthdate(iso: String): String? {
    val parts = iso.split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (month !in 1..12) return null
    return "$day ${MONTHS[month - 1]} $year"
}

/**
 * Maps the domain relationship onto the design-system control.
 *
 * [FriendshipStatus.UNKNOWN] — a state this build does not recognise — falls back to
 * ADD rather than rendering nothing, so a newer backend cannot produce a dead row.
 */
fun FriendshipStatus.friendActionState(): FriendActionState = when (this) {
    FriendshipStatus.FRIENDS -> FriendActionState.FRIENDS
    FriendshipStatus.REQUEST_SENT -> FriendActionState.REQUESTED
    FriendshipStatus.REQUEST_RECEIVED -> FriendActionState.RESPOND
    FriendshipStatus.NONE, FriendshipStatus.UNKNOWN -> FriendActionState.ADD
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
