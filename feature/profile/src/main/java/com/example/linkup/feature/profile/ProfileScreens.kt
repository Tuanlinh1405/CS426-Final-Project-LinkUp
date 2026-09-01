package com.example.linkup.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.designsystem.component.Avatar
import com.example.linkup.core.designsystem.component.ChoiceChip
import com.example.linkup.core.designsystem.component.LinkUpField
import com.example.linkup.core.designsystem.component.MediaPlaceholder
import com.example.linkup.core.designsystem.component.PrimaryButton
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.data.model.User
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft

@Composable
fun ProfileScreen(me: User, onEdit: () -> Unit, onSettings: () -> Unit) {
    var tab by remember { mutableStateOf("Posts") }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Profile", fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, modifier = Modifier.weight(1f))
            Text("⚙", fontSize = 23.sp, modifier = Modifier.clickable(onClick = onSettings))
        }
        Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.horizontalGradient(listOf(Color(0xFF44187D), Color(0xFFE73C91), Color(0xFF2835A6)))))
        Box(Modifier.fillMaxWidth().height(62.dp)) {
            Box(Modifier.padding(start = 20.dp).align(Alignment.TopStart).clip(CircleShape).background(Color.White).padding(4.dp)) { Avatar(me.initials, 84) }
            OutlinedButton(onClick = onEdit, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp), shape = RoundedCornerShape(8.dp)) { Text("Edit Profile") }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(me.name, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(me.username, color = LinkMuted)
            Text(me.bio, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Stat("248", "Posts")
                Stat("12.4K", "Followers")
                Stat("680", "Following")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Posts", "Reels", "Photos").forEach { ChoiceChip(it, selected = tab == it) { tab = it } }
            }
            Spacer(Modifier.height(16.dp))
            if (tab == "Posts") MediaPlaceholder("Latest ${me.name} post", Modifier.clip(RoundedCornerShape(12.dp)))
            else Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(12.dp)).background(LinkPurpleSoft), contentAlignment = Alignment.Center) {
                Text("$tab gallery is ready for API data", color = LinkPurple, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold)
        Text(label, color = LinkMuted, fontSize = 12.sp)
    }
}

@Composable
fun EditProfileScreen(me: User, onBack: () -> Unit, onSaved: () -> Unit) {
    var name by remember { mutableStateOf(me.name) }
    var username by remember { mutableStateOf(me.username.removePrefix("@")) }
    var bio by remember { mutableStateOf(me.bio) }
    var location by remember { mutableStateOf("San Francisco, CA") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Edit Profile", onBack, action = "Save", onAction = onSaved)
        Box(Modifier.fillMaxWidth().height(130.dp).background(Brush.horizontalGradient(listOf(Color(0xFF4A1D82), Color(0xFFFF4D98))))) {
            Text("Change cover", color = Color.White, modifier = Modifier.align(Alignment.Center).clickable {})
        }
        Box(Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).background(Color.White).padding(4.dp)) { Avatar(me.initials, 86) }
        Text("Change profile photo", color = LinkPurple, modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinkUpField(name, { name = it }, "Full name")
            LinkUpField(username, { username = it }, "Username")
            LinkUpField(bio, { bio = it }, "Bio", singleLine = false)
            LinkUpField(location, { location = it }, "Location")
            PrimaryButton("Save Changes", onSaved)
        }
    }
}
