package com.unh.hoppin_android_app

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState


// You need to add @ExperimentalPermissionsApi to use the Accompanist permission library.
@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("UnrememberedMutableState")
@Composable
fun MapScreen() {
    // 1. Define the permissions state directly in the Composable function scope
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION, // Better to use android.Manifest
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ),
    )

    // 2. Launch the permission request effect directly in the Composable function scope
    LaunchedEffect(locationPermissions.permissions) {
        // Only launch if the user hasn't granted all of them and we should show rationale or haven't asked yet
        if (!locationPermissions.allPermissionsGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // 3. Define mapUiSettings in the Composable function scope
    val mapUiSettings = MapUiSettings(
        compassEnabled = true,
        myLocationButtonEnabled = locationPermissions.allPermissionsGranted // Enable my location button only if permissions are granted
    )

    val singapore = LatLng(1.35, 103.87)
    val cameraPositionState = rememberCameraPositionState {
        // 4. Initialization logic for the camera position is correct here
        position = CameraPosition.fromLatLngZoom(singapore, 10f)
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = mapUiSettings, // Now mapUiSettings is accessible
    ) {
        Marker(
            state = MarkerState(position = singapore),
            title = "Singapore",
            snippet = "Hello Maps"
        )
    }
}