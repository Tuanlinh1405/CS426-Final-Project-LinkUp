package com.example.linkup.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.designsystem.icon.LinkUpIcons
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPink
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft
import com.example.linkup.core.navigation.AppRoute

/**
 * One tab: the outline icon when idle, the filled one when selected.
 *
 * Swapping weight rather than only colour is what makes the current tab readable at
 * a glance, and it is the convention every major social app follows.
 */
private data class BottomItem(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

private val bottomItems = listOf(
    BottomItem(AppRoute.FEED, "Home", LinkUpIcons.Home, LinkUpIcons.HomeFilled),
    BottomItem(AppRoute.REELS, "Reels", LinkUpIcons.Reels, LinkUpIcons.ReelsFilled),
    BottomItem(AppRoute.DATING_DISCOVER, "Dating", LinkUpIcons.Heart, LinkUpIcons.HeartFilled),
    BottomItem(AppRoute.CHAT_LIST, "Chats", LinkUpIcons.Chat, LinkUpIcons.ChatFilled),
    BottomItem(AppRoute.PROFILE, "Me", LinkUpIcons.Person, LinkUpIcons.PersonFilled)
)

@Composable
fun LinkUpBottomBar(
    selected: AppRoute,
    unreadChats: Int = 0,
    onNavigate: (AppRoute) -> Unit
) {
    NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
        bottomItems.forEach { item ->
            val isSelected = selected == item.route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Box {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp)
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
                },
                label = {
                    Text(
                        item.label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = LinkPurple,
                    selectedTextColor = LinkPurple,
                    unselectedIconColor = LinkMuted,
                    unselectedTextColor = LinkMuted,
                    indicatorColor = LinkPurpleSoft
                )
            )
        }
    }
}
