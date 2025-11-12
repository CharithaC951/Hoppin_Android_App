package com.unh.hoppin_android_app

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.unh.hoppin_android_app.viewmodels.ResetPasswordViewModel
import com.unh.hoppin_android_app.viewmodels.ResetStep
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordFlowScreen(
    navController: NavController,
    viewModel: ResetPasswordViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forgot Password") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (uiState.currentStep) {
                ResetStep.EnterEmail -> EnterEmailUI(
                    isLoading = uiState.isLoading,
                    onSendOtp = { email -> viewModel.sendOtp(email) }
                )
                ResetStep.EnterOtp -> EnterOtpUI(
                    isLoading = uiState.isLoading,
                    onVerifyOtp = { otp -> viewModel.verifyOtp(otp) }
                )
                ResetStep.EnterNewPassword -> EnterNewPasswordUI(
                    isLoading = uiState.isLoading,
                    onSubmit = { new, confirm -> viewModel.resetPassword(new, confirm) }
                )
                ResetStep.Success -> SuccessUI(navController = navController)
            }
        }
    }
}

@Composable
private fun EnterEmailUI(isLoading: Boolean, onSendOtp: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ... (UI code similar to ForgotPasswordScreen from previous answer)
        Text("Hoppin", fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
        Spacer(modifier = Modifier.height(48.dp))
        Text("Enter your email", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email address") },
            placeholder = { Text("@xyz.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onSendOtp(email) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            else Text("Send Otp", color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun EnterOtpUI(isLoading: Boolean, onVerifyOtp: (String) -> Unit) {
    var otp by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Hoppin", fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
        Spacer(modifier = Modifier.height(48.dp))
        Text("Enter the verification code", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = otp,
            onValueChange = { otp = it },
            label = { Text("Verification Code") },
            placeholder = { Text("123456") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onVerifyOtp(otp) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            else Text("Verify", color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun EnterNewPasswordUI(isLoading: Boolean, onSubmit: (String, String) -> Unit) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Hoppin", fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
        Spacer(modifier = Modifier.height(48.dp))
        Text("Enter the new password", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, label = { Text("New password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Confirm password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { onSubmit(newPassword, confirmPassword) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            else Text("Submit", color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun SuccessUI(navController: NavController) {
    LaunchedEffect(Unit) {
        delay(2000)
        navController.popBackStack()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Success!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Your password has been reset.", textAlign = TextAlign.Center)
    }
}