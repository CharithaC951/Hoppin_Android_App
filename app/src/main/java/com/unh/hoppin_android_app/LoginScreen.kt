@file:OptIn(ExperimentalMaterial3Api::class)

package com.unh.hoppin_android_app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

@Composable
fun LoginScreen(navController: NavController) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.hoppinbackground),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.4f
        )

        val authNavController = rememberNavController()

        NavHost(navController = authNavController, startDestination = "SignInRoute") {
            composable("SignInRoute") {
                SignInUI(
                    onNavigateToCreateAccount = { authNavController.navigate("CreateAccountRoute") },
                    onLoginSuccess = {
                        navController.navigate("Home") {
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
        }
    }
}

@Composable
private fun SignInUI(onNavigateToCreateAccount: () -> Unit, onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
                isLoading = true
                auth.signInWithCredential(credential).addOnCompleteListener { firebaseTask ->
                    if (firebaseTask.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        if (firebaseUser != null) {
                            val db = FirebaseFirestore.getInstance()
                            val userRef = db.collection("users").document(firebaseUser.uid)
                            userRef.get().addOnSuccessListener { document ->
                                if (!document.exists()) {
                                    val newUser = hashMapOf(
                                        "displayName" to firebaseUser.displayName,
                                        "email" to firebaseUser.email,
                                        "photoUrl" to firebaseUser.photoUrl.toString(),
                                        "createdAt" to com.google.firebase.Timestamp.now(),
                                        "unreadNotificationCount" to 0,
                                        "savedPlaceIds" to emptyList<String>(),
                                        "favoritePlaceIds" to emptyList<String>()
                                    )
                                    userRef.set(newUser, SetOptions.merge())
                                        .addOnSuccessListener { onLoginSuccess() }
                                        .addOnFailureListener { e ->
                                            isLoading = false
                                            Toast.makeText(context, "Failed to create profile: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                } else {
                                    onLoginSuccess()
                                }
                            }
                        }
                    } else {
                        isLoading = false
                        Toast.makeText(context, "Firebase authentication failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: ApiException) {
                isLoading = false
                Toast.makeText(context, "Google sign in failed.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Hoppin", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
        Spacer(modifier = Modifier.height(48.dp))
        Text("Login", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email or Phone number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Spacer(modifier = Modifier.height(32.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) { Toast.makeText(context, "Email and password cannot be empty.", Toast.LENGTH_SHORT).show(); return@Button }
                    isLoading = true
                    auth.signInWithEmailAndPassword(email.trim(), password.trim())
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) { onLoginSuccess() } else { Toast.makeText(context, "Invalid username or password.", Toast.LENGTH_LONG).show() }
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
            ) {
                Text("Sign in", color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Forgot password? Click here to reset", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
        Spacer(modifier = Modifier.height(32.dp))
        OrSeparator()
        Spacer(modifier = Modifier.height(16.dp))

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
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Image(painter = painterResource(id = R.drawable.google), contentDescription = "Google Logo", modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Sign in with Google", color = Color.DarkGray, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onNavigateToCreateAccount,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
        ) {
            Text("Sign up", color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

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

    if (showSuccessDialog) {
        SuccessDialog()
        LaunchedEffect(Unit) {
            delay(2500L)
            showSuccessDialog = false
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = {}, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)) },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Welcome to Hoppin", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
            Spacer(modifier = Modifier.height(26.dp))
            Text("Register", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
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

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        if (name.isBlank() || email.isBlank() || password.isBlank()) { Toast.makeText(context, "Name, email, and password cannot be empty.", Toast.LENGTH_SHORT).show(); return@Button }
                        if (password != confirmPassword) { Toast.makeText(context, "Passwords do not match.", Toast.LENGTH_SHORT).show(); return@Button }
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
                                            .addOnSuccessListener { isLoading = false; showSuccessDialog = true }
                                            .addOnFailureListener { e -> isLoading = false; Toast.makeText(context, "Failed to save user details: ${e.message}", Toast.LENGTH_LONG).show() }
                                    }
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "Account creation failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Text("Create Account", color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            TermsAndPrivacyText()
        }
    }
}

@Composable
private fun SuccessDialog() {
    AlertDialog(onDismissRequest = { }, title = { Text("Success!") }, text = { Text("Account created successfully.\nRedirecting to the login page...") }, confirmButton = {})
}

@Composable
private fun OrSeparator() {
    Text("-------------------- or --------------------", color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

@Composable
private fun TermsAndPrivacyText() {
    val annotatedString = buildAnnotatedString {
        append("By clicking continue, you agree to our ")
        pushStringAnnotation(tag = "TOS", annotation = "terms_of_service_url")
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) { append("Terms of Service") }
        pop()
        append(" and ")
        pushStringAnnotation(tag = "PRIVACY", annotation = "privacy_policy_url")
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) { append("Privacy Policy") }
        pop()
    }
    ClickableText(text = annotatedString, onClick = { offset -> /* Handle clicks */ }, style = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = Color.Gray, fontSize = 12.sp), modifier = Modifier.padding(horizontal = 16.dp))
}

@Preview(showBackground = true, name = "Sign In UI")
@Composable
private fun SignInUIPreview() {
    SignInUI(onNavigateToCreateAccount = {}, onLoginSuccess = {})
}

@Preview(showBackground = true, name = "Create Account UI")
@Composable
private fun CreateAccountUIPreview() {
    CreateAccountUI(onNavigateBack = {})
}