package com.example.linkup.feature.more.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.linkup.core.designsystem.icon.LinkUpIcons
import com.example.linkup.data.model.Notification
import com.example.linkup.data.model.NotificationType

private val AvatarFallbackBrush = Brush.linearGradient(
    listOf(Color(0xFF9F67FF), Color(0xFFFF78A5))
)

/** Glyph and colour that identify a notification kind at a glance. */
internal data class TypeAccent(val icon: ImageVector, val color: Color)

internal fun accentFor(type: NotificationType): TypeAccent = when (type) {
    NotificationType.FOLLOW -> TypeAccent(LinkUpIcons.Plus, Color(0xFF7C3AED))
    NotificationType.LIKE -> TypeAccent(LinkUpIcons.HeartFilled, Color(0xFFFF3D71))
    NotificationType.COMMENT -> TypeAccent(LinkUpIcons.Comment, Color(0xFF2F80ED))
    NotificationType.MENTION -> TypeAccent(LinkUpIcons.Sparkle, Color(0xFF00A08A))
    NotificationType.MESSAGE -> TypeAccent(LinkUpIcons.Mail, Color(0xFF2F80ED))
    NotificationType.FRIEND_REQUEST -> TypeAccent(LinkUpIcons.People, Color(0xFF7C3AED))
    NotificationType.FRIEND_ACCEPT -> TypeAccent(LinkUpIcons.Check, Color(0xFF1B7A43))
    NotificationType.DATING_MATCH -> TypeAccent(LinkUpIcons.HeartFilled, Color(0xFFE73C91))
    NotificationType.SYSTEM -> TypeAccent(LinkUpIcons.Star, Color(0xFF7C3AED))
    NotificationType.UNKNOWN -> TypeAccent(LinkUpIcons.Info, Color(0xFF777286))
}

/**
 * Actor avatar with the type badge overlaid, the pattern Facebook and Instagram
 * both use — the badge says what happened before the text is read.
 */
@Composable
fun NotificationAvatar(
    notification: Notification,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val accent = accentFor(notification.type)
    Box(modifier.size(size + 6.dp), contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier
                .size(size)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(if (notification.isSystem) accent.color.copy(alpha = 0.14f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            when {
                notification.isSystem ->
                    Icon(LinkUpIcons.Star, null, tint = accent.color, modifier = Modifier.size(21.dp))

                notification.actor.avatarUrl.isNullOrBlank() -> Box(
                    Modifier.fillMaxSize().clip(CircleShape).background(AvatarFallbackBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        notification.actor.initials,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value / 2.8f).sp
                    )
                }

                else -> AsyncImage(
                    model = notification.actor.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape).background(AvatarFallbackBrush)
                )
            }
        }

        if (!notification.isSystem) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(17.dp).clip(CircleShape).background(accent.color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(accent.icon, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }
    }
}

/**
 * The sentence for a row, with the actor's name in bold.
 *
 * A system notice is written in the app's own voice, because its actor column only
 * holds the recipient to satisfy the foreign key.
 */
fun notificationText(notification: Notification): AnnotatedString = buildAnnotatedString {
    if (notification.isSystem) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Welcome to LinkUp") }
        append(" — add a photo and a bio so people can find you.")
        return@buildAnnotatedString
    }

    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(notification.actor.displayName) }
    append(" ")
    append(actionPhrase(notification.type))
}

private fun actionPhrase(type: NotificationType): String = when (type) {
    NotificationType.FOLLOW -> "started following you."
    NotificationType.LIKE -> "liked your post."
    NotificationType.COMMENT -> "commented on your post."
    NotificationType.MENTION -> "mentioned you in a post."
    NotificationType.MESSAGE -> "sent you a message."
    NotificationType.FRIEND_REQUEST -> "sent you a friend request."
    NotificationType.FRIEND_ACCEPT -> "accepted your friend request."
    NotificationType.DATING_MATCH -> "is a new match."
    // An unfamiliar type still reads as a sentence rather than a blank row.
    NotificationType.SYSTEM, NotificationType.UNKNOWN -> "sent you a notification."
}

/** Small count bubble for the top bar bell. Caps at 99+. */
@Composable
fun NotificationBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFFF3D71)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}
