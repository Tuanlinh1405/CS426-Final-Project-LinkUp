package com.example.linkup.feature.reels

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.designsystem.icon.LinkUpIcons
import com.example.linkup.core.designsystem.component.Avatar
import com.example.linkup.core.designsystem.component.LinkUpField
import com.example.linkup.core.designsystem.component.PrimaryButton
import com.example.linkup.core.designsystem.component.ScreenHeader
import com.example.linkup.data.model.User
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple

@Composable
fun ReelsScreen(onUpload: () -> Unit, onProfile: () -> Unit) {
    var liked by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF16363A), Color(0xFF16231D), Color.Black))
        )
    ) {
        Text("Reels", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.align(Alignment.TopStart).padding(18.dp))
        Icon(LinkUpIcons.Plus, "Upload reel", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).clickable(onClick = onUpload).padding(14.dp).size(28.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp).fillMaxWidth(.78f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable(onClick = onProfile)) { Avatar("SJ", 38) }
                Text(" Sarah Jones", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text("Exploring hidden places and collecting good memories ✨", color = Color.White)
            Text("♫ original sound · Sarah", color = Color.White.copy(alpha = .7f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(18.dp))
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ReelAction(if (liked) "♥" else "♡", if (liked) "13K" else "12.9K") { liked = !liked }
            ReelAction("□", "438")
            ReelAction("↗", "Share")
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)), contentAlignment = Alignment.Center) { Icon(LinkUpIcons.Music, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun ReelAction(symbol: String, label: String, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Text(symbol, color = Color.White, fontSize = 30.sp)
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
fun UploadReelScreen(me: User, onBack: () -> Unit, onPublished: () -> Unit) {
    var selected by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("Upload Reel", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth().height(330.dp).padding(16.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEAE7F0)).clickable { selected = true },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (selected) "▶" else "＋", color = LinkPurple, fontSize = 52.sp)
                    Text(if (selected) "demo-reel.mp4 · 00:18" else "Select a video", fontWeight = FontWeight.Bold)
                    Text(if (selected) "Tap to replace" else "MP4, up to 60 seconds", color = LinkMuted, fontSize = 12.sp)
                }
            }
            LinkUpField(caption, { caption = it }, "Caption", Modifier.padding(horizontal = 16.dp), singleLine = false)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(me.initials, 34)
                Text("  Publishing as ${me.name}", color = LinkMuted)
            }
        }
        PrimaryButton("Publish Reel", onPublished, Modifier.padding(16.dp), enabled = selected)
    }
}
