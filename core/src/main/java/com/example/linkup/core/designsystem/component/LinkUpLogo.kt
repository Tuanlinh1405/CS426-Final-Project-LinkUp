package com.example.linkup.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.designsystem.theme.LinkPurple

@Composable
fun LinkUpLogo(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("∞", color = LinkPurple, fontSize = if (compact) 28.sp else 44.sp, fontWeight = FontWeight.Bold)
        if (!compact) Text("LinkUp", color = LinkPurple, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
    }
}
