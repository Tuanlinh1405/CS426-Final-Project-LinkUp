package com.example.linkup.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.unit.dp
import com.example.linkup.core.designsystem.icon.LinkUpIcons
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPink
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.navigation.AppRoute

/**
 * One tab: the outline icon when idle, the filled one when selected.
 *
 * Swapping weight rather than only colour is what makes the current tab readable at
 * a glance, and it is the convention every major social app follows.
 */
private data class NavigationItem(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

private val navigationItems = listOf(
    NavigationItem(AppRoute.FEED, "Home", LinkUpIcons.Home, LinkUpIcons.HomeFilled),
    NavigationItem(AppRoute.REELS, "Reels", LinkUpIcons.Reels, LinkUpIcons.ReelsFilled),
    NavigationItem(AppRoute.DATING_DISCOVER, "Dating", LinkUpIcons.Heart, LinkUpIcons.HeartFilled),
    NavigationItem(AppRoute.CHAT_LIST, "Chats", LinkUpIcons.Chat, LinkUpIcons.ChatFilled),
    NavigationItem(AppRoute.PROFILE, "Me", LinkUpIcons.Person, LinkUpIcons.PersonFilled)
)

@Composable
fun LinkUpTopNavigationBar(
    selected: AppRoute,
    unreadChats: Int = 0,
    onNavigate: (AppRoute) -> Unit
) {
    Surface(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        color = Color.White,
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.fillMaxWidth().height(54.dp)) {
            navigationItems.forEach { item ->
                val isSelected = selected == item.route
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onNavigate(item.route) }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) LinkPurple else LinkMuted,
                            modifier = Modifier.size(25.dp)
                        )
                        if (item.route == AppRoute.CHAT_LIST && unreadChats > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 7.dp, y = (-3).dp)
                                    .size(8.dp)
                                    .background(LinkPink, CircleShape)
                            )
                        }
                    }
                    if (isSelected) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(.68f)
                                .height(3.dp)
                                .background(LinkPurple, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

/** Kept as a source-compatible alias for previews or callers outside the app module. */
@Composable
fun LinkUpBottomBar(
    selected: AppRoute,
    unreadChats: Int = 0,
    onNavigate: (AppRoute) -> Unit,
) {
    LinkUpTopNavigationBar(
        selected = selected,
        unreadChats = unreadChats,
        onNavigate = onNavigate,
    )
}
