package com.example.linkup.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkPink
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    action: String? = null,
    onAction: () -> Unit = {},
    /** When set, the title becomes tappable — used to open a chat peer's profile. */
    onTitleClick: (() -> Unit)? = null
) {
    Surface(shadowElevation = 1.dp) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    Text("‹", fontSize = 34.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp))
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (onTitleClick != null) {
                                Modifier.clickable(onClick = onTitleClick)
                            } else {
                                Modifier
                            }
                        )
                )
                if (action != null) {
                    Text(action, color = LinkPurple, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onAction))
                }
            }
            HorizontalDivider(color = LinkDivider)
        }
    }
}

@Composable
fun LinkUpTopBar(
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onAi: () -> Unit,
    unreadNotifications: Int = 0,
    onFriends: (() -> Unit)? = null,
    pendingFriendRequests: Int = 0
) {
    Surface(shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("LinkUp", color = LinkPurple, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TopAction("⌕", onSearch)
            if (onFriends != null) {
                TopAction("☺", onFriends, badgeCount = pendingFriendRequests)
            }
            TopAction("♢", onNotifications, badgeCount = unreadNotifications)
            TopAction("AI", onAi)
        }
    }
}

@Composable
private fun TopAction(text: String, onClick: () -> Unit, badgeCount: Int = 0) {
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(LinkPurpleSoft).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = LinkPurple, fontWeight = FontWeight.Bold)
        }
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(LinkPink)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
