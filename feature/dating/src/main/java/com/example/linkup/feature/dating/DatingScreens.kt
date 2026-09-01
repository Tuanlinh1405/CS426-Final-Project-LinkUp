package com.example.linkup.feature.dating

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
import com.example.linkup.core.designsystem.component.ChoiceChip
import com.example.linkup.core.designsystem.component.LinkUpField
import com.example.linkup.core.designsystem.component.PrimaryButton
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.data.model.User
import com.example.linkup.core.designsystem.theme.LinkCanvas
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPink
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft

@Composable
fun DatingProfileScreen(me: User, onBack: () -> Unit, onExplore: () -> Unit) {
    var bio by remember { mutableStateOf("Designer, weekend hiker, and always looking for a great coffee shop.") }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        ScreenHeader("Dating Profile", onBack)
        Box(Modifier.fillMaxWidth().height(250.dp).padding(16.dp).clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(Color(0xFFFFC0D2), Color(0xFF6A3AA8)))), contentAlignment = Alignment.Center) {
            Text("${me.initials}\nProfile photo", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 24.sp)
        }
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${me.name}, 24", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
            LinkUpField(bio, { bio = it }, "About me", singleLine = false)
            Text("Interests", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Travel", "Design", "Coffee").forEach { ChoiceChip(it, selected = true) }
            }
            Text("Looking for", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ChoiceChip("Relationship", true)
                ChoiceChip("Friendship")
            }
            PrimaryButton("Explore Dating", onExplore)
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun DatingDiscoverScreen(onProfile: () -> Unit, onMatches: () -> Unit, onMatch: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    val people = listOf("Sarah, 24" to "Product designer · 3 km away", "Emma, 25" to "Photographer · 5 km away")
    val person = people[index % people.size]
    Column(Modifier.fillMaxSize().background(LinkCanvas)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Discover", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text("Matches", color = LinkPink, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onMatches))
            Text("  ⚙", modifier = Modifier.clickable(onClick = onProfile))
        }
        Column(Modifier.weight(1f).padding(horizontal = 18.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Brush.verticalGradient(listOf(Color(0xFFD8C6A5), Color(0xFF5D7158)))), contentAlignment = Alignment.Center) {
                Text("Profile photo", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(18.dp)) {
                Text(person.first, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
                Text(person.second, color = LinkMuted)
                Text("Easygoing, curious, and happiest outdoors. Looking for someone to explore the city with.", modifier = Modifier.padding(top = 10.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(22.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            DatingButton("×", Color.White, LinkMuted) { index++ }
            DatingButton("♥", LinkPink, Color.White) { onMatch() }
        }
    }
}

@Composable
private fun DatingButton(text: String, background: Color, foreground: Color, onClick: () -> Unit) {
    Box(Modifier.clip(CircleShape).background(background).clickable(onClick = onClick).padding(horizontal = 23.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = foreground, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DatingMatchScreen(onChat: () -> Unit, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF0C0B10)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(horizontalArrangement = Arrangement.Center) {
            listOf("SJ", "EC").forEach { initials ->
                Box(Modifier.padding(horizontal = 4.dp).clip(CircleShape).background(Brush.linearGradient(listOf(LinkPurple, LinkPink))).padding(32.dp)) { Text(initials, color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("It's a Match!", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 31.sp)
        Text("You and Emma liked each other", color = Color.White.copy(alpha = .7f), modifier = Modifier.padding(8.dp))
        PrimaryButton("Chat Now", onChat, Modifier.padding(top = 28.dp))
        Text("Continue Exploring", color = Color.White, modifier = Modifier.clickable(onClick = onContinue).padding(18.dp))
    }
}

@Composable
fun DatingMatchesScreen(onBack: () -> Unit, onChat: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Matches", onBack)
        Text("New matches", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("Emma" to "EC", "Amelia" to "AM", "Jenny" to "JN").forEach { (name, initials) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onChat)) {
                    Box(Modifier.clip(CircleShape).background(Brush.linearGradient(listOf(LinkPink, LinkPurple))).padding(3.dp)) {
                        Box(Modifier.clip(CircleShape).background(Color.White).padding(3.dp)) { Text(initials, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold) }
                    }
                    Text(name, fontSize = 12.sp)
                }
            }
        }
        Text("Messages", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        listOf("Emma Chen" to "Say hello to your new match!", "Jenny Miller" to "Coffee this weekend?").forEach { (name, preview) ->
            Row(Modifier.fillMaxWidth().clickable(onClick = onChat).padding(16.dp)) {
                Box(Modifier.clip(CircleShape).background(LinkPurpleSoft).padding(13.dp)) { Text(name.take(1), color = LinkPurple, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 12.dp)) { Text(name, fontWeight = FontWeight.Bold); Text(preview, color = LinkMuted, fontSize = 12.sp) }
            }
        }
    }
}
