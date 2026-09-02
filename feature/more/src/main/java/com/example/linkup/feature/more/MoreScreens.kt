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
import com.example.linkup.core.designsystem.component.Avatar
import com.example.linkup.core.designsystem.component.ChoiceChip
import com.example.linkup.core.designsystem.component.EmptyState
import com.example.linkup.core.designsystem.component.LinkUpField
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.data.model.NotificationItem
import com.example.linkup.data.model.User
import com.example.linkup.core.designsystem.theme.LinkCanvas
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft

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
