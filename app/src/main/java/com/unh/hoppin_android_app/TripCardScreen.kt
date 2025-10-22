@file:Suppress("DEPRECATION")

package com.unh.hoppin_android_app

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCardScreen(
    onBack: () -> Unit = {}
) {
    var tagline by remember { mutableStateOf(TextFieldValue("")) }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }

    // Photo Picker (Android 13+)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pickedImageUri = uri
    }

    // Legacy: request READ_EXTERNAL_STORAGE and then open GetContent
    var hasStoragePermission by remember { mutableStateOf(Build.VERSION.SDK_INT >= 33) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasStoragePermission = granted }
    val getContentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) pickedImageUri = uri
    }

    fun launchPicker() {
        if (Build.VERSION.SDK_INT >= 33) {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            if (!hasStoragePermission) {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                getContentLauncher.launch("image/*")
            }
        }
    }

    LaunchedEffect(hasStoragePermission) {
        if (Build.VERSION.SDK_INT < 33 && hasStoragePermission) {
            getContentLauncher.launch("image/*")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Cards") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                "Place name",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(10.dp))

            // Clickable image area
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
                    Image(
                        painter = painterResource(R.drawable.hoppin_logo),
                        contentDescription = "Placeholder",
                        modifier = Modifier.size(96.dp)
                    )
                } else {
                    AsyncImage(
                        model = pickedImageUri,
                        contentDescription = "Selected photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

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
                FilledTonalIconButton(
                    onClick = { },
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TripCardPreview() {
    TripCardScreen(onBack = {})
}
