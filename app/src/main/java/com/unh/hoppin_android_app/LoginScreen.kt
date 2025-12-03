@file:OptIn(ExperimentalMaterial3Api::class)

package com.unh.hoppin_android_app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable

@Composable
fun LoginScreen(navController: NavController) {
    Box(modifier = Modifier.fillMaxSize()) {
        // UPDATE: Using your specific background image name
        Image(
            painter = painterResource(id = R.drawable.test_bg),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        val authNavController = rememberNavController()

        NavHost(navController = authNavController, startDestination = "SignInRoute") {
            composable("SignInRoute") {
                SignInUI(
                    onNavigateToCreateAccount = { authNavController.navigate("CreateAccountRoute") },
                    onNavigateToForgotPassword = { authNavController.navigate("ForgotPasswordRoute") },
                    onLoginSuccess = { userName ->
                        navController.navigate("Home/$userName") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }
            composable("CreateAccountRoute") {
                CreateAccountUI(
                    onNavigateBack = { authNavController.popBackStack() }
                )
            }
            composable("ForgotPasswordRoute") {
                ForgotPasswordScreen(navController = authNavController)
            }
        }
    }
}
@Composable
private fun SignInUI(
    onNavigateToCreateAccount: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onLoginSuccess: (userName: String) -> Unit
) {
    // --- State and Logic (Unchanged) ---
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            // ... (Keep your existing Google Sign In logic here) ...
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
                isLoading = true
                auth.signInWithCredential(credential).addOnCompleteListener { firebaseTask ->
                    if (firebaseTask.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        if (firebaseUser != null) {
                            val userRef = db.collection("users").document(firebaseUser.uid)
                            userRef.get().addOnSuccessListener { document ->
                                if (!document.exists()) {
                                    val displayName = firebaseUser.displayName ?: firebaseUser.email ?: "User"
                                    val newUser = hashMapOf(
                                        "name" to displayName,
                                        "displayName" to firebaseUser.displayName,
                                        "email" to firebaseUser.email,
                                        "photoUrl" to firebaseUser.photoUrl.toString(),
                                        "createdAt" to com.google.firebase.Timestamp.now(),
                                        "unreadNotificationCount" to 0,
                                        "savedPlaceIds" to emptyList<String>(),
                                        "favoritePlaceIds" to emptyList<String>()
                                    )
                                    userRef.set(newUser, SetOptions.merge())
                                        .addOnSuccessListener {
                                            scope.launch {
                                                try {
                                                    StreakService.dailyCheckInFor(firebaseUser.uid)
                                                } catch (_: Exception) { }
                                                isLoading = false
                                                onLoginSuccess(firebaseUser.displayName ?: "User")
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            isLoading = false
                                            Toast.makeText(context, "Failed to create profile: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                } else {
                                    scope.launch {
                                        try {
                                            StreakService.dailyCheckInFor(firebaseUser.uid)
                                        } catch (_: Exception) { }
                                        isLoading = false
                                        onLoginSuccess(firebaseUser.displayName ?: "User")
                                    }
                                }
                            }.addOnFailureListener { e ->
                                isLoading = false
                                onLoginSuccess(firebaseUser.displayName ?: "User")
                            }
                        }
                    } else {
                        isLoading = false
                        Toast.makeText(context, "Authentication failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: ApiException) {
                isLoading = false
                Toast.makeText(context, "Google sign in failed.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    // --- Updated UI Layout ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        Image(
            painter = painterResource(id = R.drawable.hoppin_logo),
            contentDescription = "Hoppin Logo",
            modifier = Modifier.size(width = 200.dp, height = 200.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // "L O G I N" Text - Updated to White and Spaced to match screenshot
        Text(
            text = "LOGIN",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            letterSpacing = 8.sp, // Adds spacing between letters
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic
        )

        // Reduced Spacer (Was 48.dp) -> Pulls inputs higher
        Spacer(modifier = Modifier.height(10.dp))

        // Email Field
        TextField(
            value = email,
            onValueChange = { email = it },
            placeholder = { Text("Email or Phone number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.9f), // Semi-transparent
                unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password Field
        TextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.9f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            )
        )

        // Reduced Spacer (Was 32.dp) -> Pulls Sign In button higher
        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Email and password cannot be empty.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading = true
                    auth.signInWithEmailAndPassword(email.trim(), password.trim())
                        .addOnCompleteListener { task ->
                            // ... (Same login logic as before) ...
                            if (task.isSuccessful) {
                                val userId = auth.currentUser?.uid
                                if (userId != null) {
                                    db.collection("users").document(userId).get()
                                        .addOnSuccessListener { document ->
                                            val userName = document.getString("name") ?: auth.currentUser?.displayName ?: "User"
                                            scope.launch {
                                                try { StreakService.dailyCheckInFor(userId) } catch (_: Exception) { }
                                                isLoading = false
                                                onLoginSuccess(userName)
                                            }
                                        }
                                        .addOnFailureListener {
                                            isLoading = false
                                            onLoginSuccess(auth.currentUser?.displayName ?: "User")
                                        }
                                }
                            } else {
                                Toast.makeText(context, "Invalid username or password.", Toast.LENGTH_LONG).show()
                                isLoading = false
                            }
                        }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004D40))
            ) {
                Text("Sign in", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(1.dp))

        Text(
            "Forgot password? Click here to reset",
            modifier = Modifier.clickable { onNavigateToForgotPassword() },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic
        )

        // Reduced Spacer (Was 32.dp) -> Pulls bottom section higher
        Spacer(modifier = Modifier.height(20.dp))
        OrSeparator()
        Spacer(modifier = Modifier.height(20.dp))

        // --- UPDATED: Google Button with Low Transparency ---
        Button(
            onClick = {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken("590816117011-o42oiclbce1r79urio3o02qgsr9k20lu.apps.googleusercontent.com")
                    .requestEmail()
                    .build()
                val googleSignInClient = GoogleSignIn.getClient(context, gso)
                googleSignInClient.signOut().addOnCompleteListener {
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(30.dp),
            // Changed alpha to 0.8f to make it semi-transparent
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.google),
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Sign in with Google", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- UPDATED: Sign Up Button Color & Position ---
        // Reduced spacers above mean this button will now sit higher up.
        Button(
            onClick = onNavigateToCreateAccount,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(30.dp),
            // Changed color to Cyan to match screenshot
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004D40))
        ) {
            Text("Sign up", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// --- Unchanged Composables Below ---
@Composable
private fun CreateAccountUI(onNavigateBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showSuccessDialog by remember { mutableStateOf(false) }

    // Define the common styling for text fields to ensure consistency
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        disabledContainerColor = Color.Gray.copy(alpha = 0.8f),
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black,
        focusedPlaceholderColor = Color.Gray,
        unfocusedPlaceholderColor = Color.Gray,
        focusedLabelColor = Color.DarkGray,
        unfocusedLabelColor = Color.Gray
    )

    val textFieldShape = RoundedCornerShape(10.dp)

    if (showSuccessDialog) {
        SuccessDialog()
        LaunchedEffect(Unit) {
            delay(2500L)
            showSuccessDialog = false
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        // Changed Icon color to Black so it stands out against the light sky
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title Text
            Text(
                "WELCOME TO HOPPIN",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.8f), // Darker for visibility on sky
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "REGISTER",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.8f),
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic

            )
            Spacer(modifier = Modifier.height(24.dp))

            // -- Input Fields --
            // Changed from OutlinedTextField to TextField for better visibility
            TextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Name") }, // Using placeholder instead of label looks cleaner on forms
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = textFieldColors,
                shape = textFieldShape
            )
            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = textFieldColors,
                shape = textFieldShape
            )
            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                placeholder = { Text("Phone number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = textFieldColors,
                shape = textFieldShape
            )
            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = textFieldColors,
                shape = textFieldShape
            )
            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                placeholder = { Text("Confirm password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = textFieldColors,
                shape = textFieldShape
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Button(
                    onClick = {
                        if (name.isBlank() || email.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Name, email, and password cannot be empty.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (password != confirmPassword) {
                            Toast.makeText(context, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        val auth = FirebaseAuth.getInstance()
                        auth.createUserWithEmailAndPassword(email.trim(), password.trim())
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val userId = auth.currentUser?.uid
                                    if (userId != null) {
                                        val db = FirebaseFirestore.getInstance()
                                        val userMap = hashMapOf("name" to name.trim(), "email" to email.trim(), "phoneNumber" to phoneNumber.trim())
                                        db.collection("users").document(userId).set(userMap)
                                            .addOnSuccessListener {
                                                isLoading = false
                                                showSuccessDialog = true
                                            }
                                            .addOnFailureListener { e ->
                                                isLoading = false
                                                Toast.makeText(context, "Failed to save user details: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                    } else {
                                        isLoading = false
                                        Toast.makeText(context, "Account created but user id missing.", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "Account creation failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(30.dp),
                    // Matched the button color to your Login Screen (Cyan)
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004D40))
                ) {
                    Text("Create Account", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.weight(1f))

            // Added a background box or improved text color for the terms text
            Box(
                modifier = Modifier
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                TermsAndPrivacyText()
            }
        }
    }
}

@Composable
private fun SuccessDialog() {
    AlertDialog(
        onDismissRequest = { /* intentionally empty */ },
        title = { Text("Success!") },
        text = { Text("Account created successfully.\nRedirecting to the login page...") },
        confirmButton = {}
    )
}

@Composable
private fun OrSeparator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Divider(
            modifier = Modifier.weight(1f),
            color = Color.Black,
            thickness = 1.dp
        )
        Text(
            text = " OR ",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Divider(
            modifier = Modifier.weight(1f),
            color = Color.Black,
            thickness = 1.dp
        )
    }
}

@Composable
private fun TermsAndPrivacyText() {
    val annotatedString = buildAnnotatedString {
        withStyle(style = SpanStyle(
            color = Color.Black,       // Change to Color.Black if you have a light background
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp
        )) {
            append("By clicking continue, you agree to our ")
        }
        pushStringAnnotation(tag = "TOS", annotation = "terms_of_service_url")
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.Black)) {
            append("Terms of Service")
        }
        pop()

        withStyle(style = SpanStyle(color = Color.Black)) {   // <-- change "and" color
            append(" and ")
        }
        pushStringAnnotation(tag = "PRIVACY", annotation = "privacy_policy_url")
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.Black)) {
            append("Privacy Policy")
        }
        pop()
    }
    ClickableText(text = annotatedString, onClick = { }, style = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = Color.Gray, fontSize = 12.sp), modifier = Modifier.padding(horizontal = 16.dp))
}

@Preview(showBackground = true, name = "Sign In UI")
@Composable
private fun SignInUIPreview() {
    SignInUI(
        onNavigateToCreateAccount = {},
        onNavigateToForgotPassword = {},
        onLoginSuccess = {}
    )
}

@Preview(showBackground = true, name = "Create Account UI")
@Composable
private fun CreateAccountUIPreview() {
    CreateAccountUI(onNavigateBack = {})
}