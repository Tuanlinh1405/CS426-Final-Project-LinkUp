package com.example.linkup.feature.auth.register

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.linkup.core.ui.ChoiceChip
import com.example.linkup.core.ui.LinkUpField
import com.example.linkup.core.ui.LinkUpLogo
import com.example.linkup.core.ui.PrimaryButton
import com.example.linkup.ui.theme.LinkMuted

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onRegistered: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Female") }

    LaunchedEffect(uiState) {
        if (uiState is RegisterUiState.Success) {
            onRegistered()
            viewModel.resetState()
        }
    }

    val isLoading = uiState is RegisterUiState.Loading
    val errorMessage = (uiState as? RegisterUiState.Error)?.message

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 34.sp, modifier = Modifier.clickable(onClick = onBack, enabled = !isLoading).padding(end = 12.dp))
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

            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(14.dp))
            Text("Gender", fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Female", "Male", "Other").forEach { value ->
                    ChoiceChip(value, selected = gender == value) { if (!isLoading) gender = value }
                }
            }
            Spacer(Modifier.height(10.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
            } else {
                PrimaryButton(
                    text = "Create Account",
                    onClick = { viewModel.register(email, username, password, name) },
                    enabled = name.isNotBlank() && email.contains("@") && password.length >= 4
                )
            }

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
