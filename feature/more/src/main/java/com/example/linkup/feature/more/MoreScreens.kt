package com.example.linkup.feature.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.ui.Avatar
import com.example.linkup.core.ui.ChoiceChip
import com.example.linkup.core.ui.EmptyState
import com.example.linkup.core.ui.LinkUpField
import com.example.linkup.core.ui.ScreenHeader
import com.example.linkup.data.model.NotificationItem
import com.example.linkup.data.model.User
import com.example.linkup.ui.theme.LinkCanvas
import com.example.linkup.ui.theme.LinkDivider
import com.example.linkup.ui.theme.LinkMuted
import com.example.linkup.ui.theme.LinkPurple
import com.example.linkup.ui.theme.LinkPurpleSoft

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenProfile: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("People") }
    val people = listOf(
        User("s1", "John Doe", "@john.doe", "JD"),
        User("s2", "Alice Brown", "@alice", "AB"),
        User("s3", "George Clark", "@george", "GC")
    ).filter { it.name.contains(query, true) || query.isBlank() }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("Search", onBack)
        LinkUpField(query, { query = it }, "Search LinkUp", Modifier.padding(16.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("People", "Posts", "Reels").forEach { ChoiceChip(it, selected = tab == it) { tab = it } }
        }
        if (tab != "People") EmptyState("No $tab yet", "API results will appear here")
        else LazyColumn(Modifier.padding(top = 10.dp)) {
            items(people, key = { it.id }) { user ->
                Row(Modifier.fillMaxWidth().clickable(onClick = onOpenProfile).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user.initials, 46)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(user.name, fontWeight = FontWeight.Bold); Text(user.username, color = LinkMuted) }
                    Box(Modifier.clip(CircleShape).background(LinkPurpleSoft).padding(horizontal = 14.dp, vertical = 7.dp)) { Text("Follow", color = LinkPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(notifications: List<NotificationItem>, onBack: () -> Unit, onOpen: () -> Unit) {
    var unreadOnly by remember { mutableStateOf(false) }
    val visible = if (unreadOnly) notifications.filter { it.unread } else notifications
    Column(Modifier.fillMaxSize().background(Color.White)) {
        ScreenHeader("Notifications", onBack, action = "Mark all read")
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("All", !unreadOnly) { unreadOnly = false }
            ChoiceChip("Unread", unreadOnly) { unreadOnly = true }
        }
        LazyColumn {
            items(visible, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().background(if (item.unread) LinkPurpleSoft.copy(alpha = .45f) else Color.White).clickable(onClick = onOpen).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(item.actor.initials, 44)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(item.actor.name, fontWeight = FontWeight.Bold)
                        Text(item.text, fontSize = 13.sp)
                    }
                    Text(item.time, color = LinkMuted, fontSize = 11.sp)
                }
                HorizontalDivider(color = LinkDivider)
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit, onDatingProfile: () -> Unit) {
    var darkMode by remember { mutableStateOf(false) }
    var notifications by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().background(LinkCanvas)) {
        ScreenHeader("Settings", onBack)
        Column(Modifier.background(Color.White)) {
            SettingRow("Account", "Profile, password and security")
            SettingRow("Dating profile", "Photos, interests and preferences", onDatingProfile)
            ToggleRow("Notifications", "Push and in-app alerts", notifications) { notifications = it }
            ToggleRow("Dark mode", "Use a darker appearance", darkMode) { darkMode = it }
            SettingRow("Privacy", "Blocked users and visibility")
            SettingRow("Server (debug)", "10.0.2.2:8080")
            SettingRow("About LinkUp", "Version 1.0.0")
            Row(Modifier.fillMaxWidth().clickable(onClick = onLogout).padding(18.dp)) { Text("Log out", color = Color(0xFFE23C5B), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = LinkMuted, fontSize = 12.sp) }
        Text("›", color = LinkMuted, fontSize = 24.sp)
    }
    HorizontalDivider(color = LinkDivider)
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = LinkMuted, fontSize = 12.sp) }
        Switch(checked, onChecked)
    }
    HorizontalDivider(color = LinkDivider)
}
