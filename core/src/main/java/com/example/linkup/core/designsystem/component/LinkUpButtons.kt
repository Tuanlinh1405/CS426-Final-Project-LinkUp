package com.example.linkup.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.designsystem.motion.Motion
import com.example.linkup.core.designsystem.motion.pressScale
import com.example.linkup.core.designsystem.theme.LinkDivider
import com.example.linkup.core.designsystem.theme.LinkPurple

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth().height(50.dp).pressScale(interactionSource),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LinkPurple)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    val chipInteraction = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        targetValue = if (selected) LinkPurple else Color.White,
        animationSpec = tween(Motion.QUICK_MS),
        label = "chipBackground"
    )
    val outline by animateColorAsState(
        targetValue = if (selected) LinkPurple else LinkDivider,
        animationSpec = tween(Motion.QUICK_MS),
        label = "chipOutline"
    )
    val label by animateColorAsState(
        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(Motion.QUICK_MS),
        label = "chipLabel"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(1.dp, outline, RoundedCornerShape(50))
            .pressScale(chipInteraction)
            .clickable(
                interactionSource = chipInteraction,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = text, color = label, fontSize = 12.sp)
    }
}
