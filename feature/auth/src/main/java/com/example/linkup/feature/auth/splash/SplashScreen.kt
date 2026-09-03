package com.example.linkup.feature.auth.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SplashScreen(
    onAuthenticated: () -> Unit = {},
    onUnauthenticated: () -> Unit = {},
    viewModel: SplashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        when (uiState) {
            SplashUiState.Authenticated -> onAuthenticated()
            SplashUiState.Unauthenticated -> onUnauthenticated()
            SplashUiState.Loading -> { /* Keep waiting */ }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF8B3DFF), Color(0xFF6D2CE8)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = .16f))
                    .padding(22.dp)
            ) {
                Text("∞", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 58.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("LinkUp", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
            Text("Connect. Share. Discover.", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
            Spacer(Modifier.height(44.dp))
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        }
    }
}
