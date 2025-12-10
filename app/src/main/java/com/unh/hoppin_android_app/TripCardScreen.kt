@file:Suppress("DEPRECATION")

package com.unh.hoppin_android_app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * TripCardScreen
 *
 * Screen for creating a “Trip Card” — a visual card that includes:
 *  - The place name
 *  - A selected photo (from gallery or picker)
 *  - A tagline or comment
 *  - A share button to share the image/text externally
 *
 * Features:
 *  • Supports both Android 13+ Photo Picker (secure)
 *  • Fallback for legacy (pre-Android 13) with READ_EXTERNAL_STORAGE permission
 *  • Shares text and image using system share sheet
 *
 * @param placeName Name of the place this trip card is for.
 * @param onBack Callback when back button is pressed (used by navigation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCardScreen(
    placeName: String,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    var tagline by remember { mutableStateOf(TextFieldValue("")) }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }

    // --- Photo Picker (Android 13+ API 33+) ---
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) pickedImageUri = uri }

    var hasStoragePermission by remember { mutableStateOf(Build.VERSION.SDK_INT >= 33) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasStoragePermission = granted }

    val getContentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) pickedImageUri = uri }

    /**
     * Launches the appropriate image picker depending on Android version:
     *  - For Android 13+: uses the secure Photo Picker
     *  - For older devices: requests READ_EXTERNAL_STORAGE (if needed) and uses GetContent
     */
    fun launchPicker() {
        if (Build.VERSION.SDK_INT >= 33) {
            // Use new Photo Picker (Android 13+)
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            // Legacy path — check permission first
            if (!hasStoragePermission) {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                getContentLauncher.launch("image/*")
            }
        }
    }

    /**
     * Automatically triggers the legacy picker once permission is granted (pre-Android 13 only)
     */
    LaunchedEffect(hasStoragePermission) {
        if (Build.VERSION.SDK_INT < 33 && hasStoragePermission) {
            getContentLauncher.launch("image/*")
        }
    }

    /**
     * Shares the selected image (if any) and tagline text using system share sheet.
     * The text always includes the place name if available.
     */
    fun share() {
        val composedText = buildString {
            if (placeName.isNotBlank()) {
                appendLine(placeName)
            }
            val tag = tagline.text.trim()
            if (tag.isNotEmpty()) {
                if (placeName.isNotBlank()) appendLine()
                append(tag)
            }
        }.ifBlank { null }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (pickedImageUri != null) "image/*" else "text/plain"
            composedText?.let { putExtra(Intent.EXTRA_TEXT, it) }
            pickedImageUri?.let { uri ->
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startActivity(Intent.createChooser(send, "Share via"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Cards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =  if(!isSystemInDarkTheme()){ TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xfff8f0e3),
                    titleContentColor = Color(0xFF000000)
                )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                }
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Title: actual place name from navigation
            Text(
                text = placeName.ifBlank { "Trip Card" },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(Modifier.height(10.dp))

            // --- Image Picker Area ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFF2F2F2))
                    .clickable { launchPicker() },
                contentAlignment = Alignment.Center
            ) {
                if (pickedImageUri == null) {
                    // Show placeholder when no photo selected
                    Image(
                        painter = painterResource(R.drawable.hoppinbackground),
                        contentDescription = "Placeholder",
                        modifier = Modifier.size(96.dp)
                    )
                } else {
                    // Show selected image
                    AsyncImage(
                        model = pickedImageUri,
                        contentDescription = "Selected photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Tagline Input + Share Button Row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = tagline,
                    onValueChange = { tagline = it },
                    placeholder = { Text("Add your tagline or comment") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = { share() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
            }
        }
    }
}

/**
 * Preview for TripCardScreen
 * - Displays a static view of the composable without running logic
 */
@Preview(showBackground = true)
@Composable
private fun TripCardPreview() {
    TripCardScreen(
        placeName = "Sample Place",
        onBack = {}
    )
}
