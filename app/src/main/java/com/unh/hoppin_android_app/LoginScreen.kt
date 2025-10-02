package com.unh.hoppin_android_app // Your specific package name

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController


/**
 * This is the main entry point for the authentication flow, called from MainActivity.
 * It sets up its own internal navigation between the Sign In and Create Account screens.
 */
@Composable
fun LoginScreen(navController: NavController) {
    // A Box is used to layer composables. We'll put the background image here first,
    // and then the rest of the UI will be drawn on top of it.
    Box(modifier = Modifier.fillMaxSize()) {
        // The background image itself
        Image(
            painter = painterResource(id = R.drawable.hoppinbackground),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, // Crop the image to fill the screen without distortion
            alpha = 0.3f // Set the transparency to 20%
        )

        // This NavController is for navigating WITHIN the login/signup flow
        val authNavController = rememberNavController()

        NavHost(navController = authNavController, startDestination = "SignInRoute") {
            composable("SignInRoute") {
                SignInUI(
                    onNavigateToCreateAccount = { authNavController.navigate("CreateAccountRoute") },
                    onLoginSuccess = {
                        // Use the main NavController (from MainActivity) to go to the Home screen
                        navController.navigate("Home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
            composable("CreateAccountRoute") {
                CreateAccountUI(
                    onNavigateBack = { authNavController.popBackStack() },
                    onRegistrationSuccess = {
                        // After creating an account, also navigate to Home
                        navController.navigate("Home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}


/**
 * This composable contains only the UI for the Sign In page.
 */
@Composable
private fun SignInUI(onNavigateToCreateAccount: () -> Unit, onLoginSuccess: () -> Unit) {
    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HoppinLogo() // The larger logo will appear here
        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(value = emailOrPhone, onValueChange = { emailOrPhone = it }, label = { Text("Email or Phone number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onLoginSuccess() /* TODO: Add real login logic */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
        ) {
            Text("Sign in", color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNavigateToCreateAccount,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
        ) {
            Text("Create Account", color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
        OrSeparator()
    }
}

/**
 * This composable contains only the UI for the Create Account page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAccountUI(onNavigateBack: () -> Unit, onRegistrationSuccess: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Using Scaffold to easily place the TopAppBar with the back button
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                // Make the TopAppBar transparent to see the background image
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        // Make the main content area transparent as well
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HoppinLogo() // The larger logo will appear here
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = phoneNumber, onValueChange = { phoneNumber = it }, label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Confirm password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { onRegistrationSuccess() /* TODO: Add real registration logic */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Text("Create Account", color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            TermsAndPrivacyText()
        }
    }
}


//region Helper and Preview Composables
@Composable
private fun HoppinLogo() {
    Image(
        painter = painterResource(id = R.drawable.hoppin_logo),
        contentDescription = "Hoppin Logo",
        // CHANGED: Increased the height from 60.dp to 90.dp to make the logo bigger
        modifier = Modifier.height(190.dp)
    )
}

@Composable
private fun OrSeparator() {
    Text(
        "-------------------- or --------------------",
        color = Color.Gray, // Changed to Gray for better contrast on the new background
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun TermsAndPrivacyText() {
    val annotatedString = buildAnnotatedString {
        append("By clicking continue, you agree to our ")
        pushStringAnnotation(tag = "TOS", annotation = "terms_of_service_url")
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
            append("Terms of Service")
        }
        pop()
        append(" and ")
        pushStringAnnotation(tag = "PRIVACY", annotation = "privacy_policy_url")
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
            append("Privacy Policy")
        }
        pop()
    }

    ClickableText(
        text = annotatedString,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "TOS", start = offset, end = offset).firstOrNull()?.let {
                Log.d("ClickableText", "Clicked on Terms of Service")
            }
            annotatedString.getStringAnnotations(tag = "PRIVACY", start = offset, end = offset).firstOrNull()?.let {
                Log.d("ClickableText", "Clicked on Privacy Policy")
            }
        },
        style = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = Color.Gray, fontSize = 12.sp),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Preview(showBackground = true, name = "Sign In UI")
@Composable
private fun SignInUIPreview() {
    SignInUI(onNavigateToCreateAccount = {}, onLoginSuccess = {})
}

@Preview(showBackground = true, name = "Create Account UI")
@Composable
private fun CreateAccountUIPreview() {
    CreateAccountUI(onNavigateBack = {}, onRegistrationSuccess = {})
}
//endregion