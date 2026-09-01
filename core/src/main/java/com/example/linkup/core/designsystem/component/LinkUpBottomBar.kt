package com.example.linkup.core.designsystem.component

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.navigation.AppRoute

private val bottomItems = listOf(
    AppRoute.FEED to "Home",
    AppRoute.REELS to "Reels",
    AppRoute.DATING_DISCOVER to "Dating",
    AppRoute.CHAT_LIST to "Chats",
    AppRoute.PROFILE to "Me"
)

@Composable
fun LinkUpBottomBar(selected: AppRoute, onNavigate: (AppRoute) -> Unit) {
    NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
        bottomItems.forEach { (route, label) ->
            NavigationBarItem(
                selected = selected == route,
                onClick = { onNavigate(route) },
                icon = { Text(if (selected == route) "●" else "○", color = if (selected == route) LinkPurple else LinkMuted) },
                label = { Text(label, fontSize = 10.sp) }
            )
        }
    }
}
