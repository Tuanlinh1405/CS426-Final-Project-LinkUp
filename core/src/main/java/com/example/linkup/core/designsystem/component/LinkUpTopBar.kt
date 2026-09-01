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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkPurple
import com.example.linkup.core.designsystem.theme.LinkPurpleSoft

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    action: String? = null,
    onAction: () -> Unit = {}
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
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                if (action != null) {
                    Text(action, color = LinkPurple, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onAction))
                }
            }
            HorizontalDivider(color = LinkDivider)
        }
    }
}

@Composable
fun LinkUpTopBar(onSearch: () -> Unit, onNotifications: () -> Unit, onAi: () -> Unit) {
    Surface(shadowElevation = 1.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("LinkUp", color = LinkPurple, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TopAction("⌕", onSearch)
            TopAction("♢", onNotifications)
            TopAction("AI", onAi)
        }
    }
}

@Composable
private fun TopAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).background(LinkPurpleSoft).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = LinkPurple, fontWeight = FontWeight.Bold)
    }
}
