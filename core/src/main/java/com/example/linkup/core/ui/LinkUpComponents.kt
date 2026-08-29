package com.example.linkup.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.navigation.AppRoute
import com.example.linkup.ui.theme.LinkDivider
import com.example.linkup.ui.theme.LinkMuted
import com.example.linkup.ui.theme.LinkPurple
import com.example.linkup.ui.theme.LinkPurpleSoft

@Composable
fun LinkUpLogo(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("∞", color = LinkPurple, fontSize = if (compact) 28.sp else 44.sp, fontWeight = FontWeight.Bold)
        if (!compact) Text("LinkUp", color = LinkPurple, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun Avatar(initials: String, size: Int = 42) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF9F67FF), Color(0xFFFF78A5)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = Color.White, fontSize = (size / 3).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LinkUpField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LinkPurple)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, action: String? = null, onAction: () -> Unit = {}) {
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
    ) { Text(text, color = LinkPurple, fontWeight = FontWeight.Bold) }
}

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

@Composable
fun MediaPlaceholder(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFFDBC8FF), Color(0xFFFFD6E3), Color(0xFFC8E7FF)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("◇", color = Color.White, fontSize = 48.sp)
            Text(label, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyState(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(LinkPurpleSoft), contentAlignment = Alignment.Center) {
            Text("∞", color = LinkPurple, fontSize = 28.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(message, color = LinkMuted)
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean = false, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) LinkPurple else Color.White)
            .border(1.dp, if (selected) LinkPurple else LinkDivider, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) }
}
