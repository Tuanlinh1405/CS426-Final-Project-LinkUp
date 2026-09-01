package com.example.linkup.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = LinkPurple,
    secondary = LinkPink,
    background = LinkInk,
    surface = Color(0xFF272238)
)

private val LightColorScheme = lightColorScheme(
    primary = LinkPurple,
    secondary = LinkPink,
    background = LinkCanvas,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = LinkInk,
    onSurface = LinkInk,
    outline = LinkDivider
)

@Composable
fun LinkUpTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
