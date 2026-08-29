package com.example.linkup.feature.auth

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.linkup.core.ui.ChoiceChip
import com.example.linkup.core.ui.LinkUpField
import com.example.linkup.core.ui.LinkUpLogo
import com.example.linkup.core.ui.PrimaryButton
import com.example.linkup.ui.theme.LinkMuted
import com.example.linkup.ui.theme.LinkPurple

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF8B3DFF), Color(0xFF6D2CE8)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = .16f)).padding(22.dp)) {
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

@Composable
fun LoginScreen(onLogin: () -> Unit, onRegister: () -> Unit) {
    var email by remember { mutableStateOf("sarah@linkup.demo") }
    var password by remember { mutableStateOf("password") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinkUpLogo()
        Spacer(Modifier.height(18.dp))
        Text("Welcome back!", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text("Sign in to continue to your community", color = LinkMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(34.dp))
        LinkUpField(email, { email = it; error = null }, "Email or username")
        Spacer(Modifier.height(12.dp))
        LinkUpField(password, { password = it; error = null }, "Password", visualTransformation = PasswordVisualTransformation())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Text("Forgot password?", color = LinkPurple, modifier = Modifier.align(Alignment.End).padding(vertical = 14.dp))
        PrimaryButton("Login", onClick = {
            if (email.isBlank() || password.length < 4) error = "Please enter a valid account" else onLogin()
        })
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.Center) {
            Text("New to LinkUp? ", color = LinkMuted)
            Text("Create account", color = LinkPurple, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onRegister))
        }
        Spacer(Modifier.height(30.dp))
        Text("Demo account is filled in — tap Login to explore", color = LinkMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun RegisterScreen(onBack: () -> Unit, onRegistered: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Female") }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 34.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp))
            LinkUpLogo(compact = true)
        }
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Join LinkUp", fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            Text("Create your profile and start connecting", color = LinkMuted)
            Spacer(Modifier.height(22.dp))
            LinkUpField(name, { name = it }, "Full name")
            Spacer(Modifier.height(10.dp))
            LinkUpField(username, { username = it }, "Username")
            Spacer(Modifier.height(10.dp))
            LinkUpField(email, { email = it }, "Email")
            Spacer(Modifier.height(10.dp))
            LinkUpField(password, { password = it }, "Password", visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(14.dp))
            Text("Gender", fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Female", "Male", "Other").forEach { value ->
                    ChoiceChip(value, selected = gender == value) { gender = value }
                }
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Create Account", onRegistered, enabled = name.isNotBlank() && email.contains("@") && password.length >= 4)
            Text(
                "By creating an account, you agree to the Terms and Privacy Policy.",
                color = LinkMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
            )
        }
    }
}
