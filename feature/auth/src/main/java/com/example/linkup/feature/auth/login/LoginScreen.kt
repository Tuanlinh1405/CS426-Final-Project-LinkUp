package com.example.linkup.feature.auth.login

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
import com.example.linkup.core.designsystem.component.LinkUpField
import com.example.linkup.core.designsystem.component.LinkUpLogo
import com.example.linkup.core.designsystem.component.PrimaryButton
import com.example.linkup.core.designsystem.theme.LinkMuted
import com.example.linkup.core.designsystem.theme.LinkPurple

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRegister: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
            viewModel.resetState()
        }
    }

    val isLoading = uiState is LoginUiState.Loading
    val errorMessage = (uiState as? LoginUiState.Error)?.message

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinkUpLogo()
        Spacer(Modifier.height(18.dp))
        Text("Welcome back!", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        Text("Sign in to continue to your community", color = LinkMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(34.dp))

        LinkUpField(
            value = email,
            onValueChange = {
                email = it
                if (uiState is LoginUiState.Error) viewModel.resetState()
            },
            label = "Email or username"
        )
        Spacer(Modifier.height(12.dp))

        LinkUpField(
            value = password,
            onValueChange = {
                password = it
                if (uiState is LoginUiState.Error) viewModel.resetState()
            },
            label = "Password",
            visualTransformation = PasswordVisualTransformation()
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }

        Text(
            text = "Forgot password?",
            color = LinkPurple,
            modifier = Modifier
                .align(Alignment.End)
                .padding(vertical = 14.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        } else {
            PrimaryButton(
                text = "Login",
                onClick = { viewModel.login(email, password) }
            )
        }

        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.Center) {
            Text("New to LinkUp? ", color = LinkMuted)
            Text(
                text = "Create account",
                color = LinkPurple,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onRegister, enabled = !isLoading)
            )
        }
    }
}
