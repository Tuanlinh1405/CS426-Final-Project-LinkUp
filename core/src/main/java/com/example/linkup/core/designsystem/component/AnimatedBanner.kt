package com.example.linkup.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.designsystem.icon.LinkUpIcons
import com.example.linkup.core.designsystem.motion.Motion

private val ErrorBackground = Color(0xFFFDECEF)
private val ErrorForeground = Color(0xFFB3261E)
private val SuccessBackground = Color(0xFFE9F7EF)
private val SuccessForeground = Color(0xFF1B7A43)

/**
 * Inline feedback strip that expands into place and collapses away.
 *
 * A banner that simply appears makes the content below jump; expanding the height
 * pushes it down smoothly and pulls it back on dismiss. The last message is kept
 * while the exit animation runs, so the text does not vanish mid-collapse.
 *
 * @param message null hides the banner.
 */
@Composable
fun AnimatedBanner(
    message: String?,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastMessage by remember { mutableStateOf("") }
    var lastIsError by remember { mutableStateOf(false) }
    if (message != null) {
        lastMessage = message
        lastIsError = isError
    }

    AnimatedVisibility(
        visible = message != null,
        enter = expandVertically(tween(Motion.MEDIUM_MS)) + fadeIn(tween(Motion.MEDIUM_MS)),
        exit = shrinkVertically(tween(Motion.QUICK_MS)) + fadeOut(tween(Motion.QUICK_MS))
    ) {
        val foreground = if (lastIsError) ErrorForeground else SuccessForeground
        Row(
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (lastIsError) ErrorBackground else SuccessBackground)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(lastMessage, color = foreground, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(
                imageVector = LinkUpIcons.Close,
                contentDescription = "Dismiss",
                tint = foreground,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp)
                    .clickable(onClick = onDismiss)
            )
        }
    }
}
