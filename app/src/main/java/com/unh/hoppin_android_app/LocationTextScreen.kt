package com.unh.hoppin_android_app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.Locale

// Constants for ensuring a fresh location (10 seconds old at most)
private const val MAX_LOCATION_AGE_MS = 10000L

@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun LocationTextScreen() {
    val context = LocalContext.current

    var locationText by remember { mutableStateOf("Initializing...") }
    var isLoading by remember { mutableStateOf(false) }

    // 1. Setup and request location permissions
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ),
    )

    // Launch permission request when the Composable first appears
    LaunchedEffect(Unit) {
        if (!locationPermissions.allPermissionsGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // 2. Fetch location and convert to address once permissions are granted
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            locationText = "Permissions granted. Attempting to get fresh address..."
            isLoading = true

            getCurrentAddress(context) { address ->
                locationText = "Current Location:\n$address"
                isLoading = false
            }
        } else if (locationPermissions.shouldShowRationale) {
            locationText = "Location permissions are required to display your nearby address."
            isLoading = false
        } else {
            locationText = "Location permissions denied. Please enable them in settings."
            isLoading = false
        }
    }

    // 3. Display the status/address text
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.padding(bottom = 16.dp))
        }
        Text(text = locationText)
    }
}

// -------------------------------------------------------------
// LOCATION AND GEOCODING HELPER FUNCTIONS
// -------------------------------------------------------------

/**
 * Requests a single, fresh location update with maximum accuracy.
 */
@SuppressLint("MissingPermission")
fun getCurrentAddress(context: Context, onAddressResult: (String) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    var resultHandled = false

    // 1. Define the location request settings (PRIORITY_HIGH_ACCURACY)
    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
        .setMaxUpdateDelayMillis(1000L) // Get update within 1s if possible
        .setDurationMillis(30000) // Timeout after 30s
        .setMaxUpdates(1) // Only need one update
        .build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            fusedLocationClient.removeLocationUpdates(this)
            if (resultHandled) return

            val location = locationResult.lastLocation
            if (location != null) {
                resultHandled = true
                val address = getAddressFromLatLng(context, location.latitude, location.longitude)
                onAddressResult(address)
            } else {
                if (!resultHandled) {
                    resultHandled = true
                    onAddressResult("Error: Could not obtain a fresh location update. Check GPS.")
                }
            }
        }
    }

    // 2. Start the fresh location request
    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)

    // 3. Check last known location ONLY if it's recent (less than 10 seconds old)
    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        if (location != null && !resultHandled) {
            val locationAge = System.currentTimeMillis() - location.time

            if (locationAge < MAX_LOCATION_AGE_MS) {
                resultHandled = true
                fusedLocationClient.removeLocationUpdates(locationCallback) // Cancel the pending request
                val address = getAddressFromLatLng(context, location.latitude, location.longitude)
                onAddressResult(address)
            }
        }
    }
}

/**
 * Performs Reverse Geocoding and returns the best available address string,
 * using all available fields if a full street address is not found.
 */
fun getAddressFromLatLng(context: Context, latitude: Double, longitude: Double): String {
    val geocoder = Geocoder(context, Locale.getDefault())

    if (!Geocoder.isPresent()) {
        return "Error: Geocoding service is not available on this device."
    }

    // Logic for API 33+ (TIRAMISU) asynchronous Geocoder vs. older synchronous versions
    val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            val result = mutableListOf<android.location.Address>()
            geocoder.getFromLocation(latitude, longitude, 1) { list -> result.addAll(list) }
            result
        } catch (e: Exception) {
            return "Geocoding Error (API 33+): ${e.message}"
        }
    } else {
        @Suppress("DEPRECATION")
        try {
            geocoder.getFromLocation(latitude, longitude, 1)
        } catch (e: Exception) {
            return "Geocoding Error (API < 33): ${e.message}"
        }
    }

    val latStr = String.format("%.6f", latitude)
    val lonStr = String.format("%.6f", longitude)

    return if (addresses.isNullOrEmpty()) {
        // If Geocoder returns NOTHING, show coordinates as the only fallback
        "No nearby address found by Geocoder.\nCoordinates:\nLat: $latStr\nLon: $lonStr"
    } else {
        // We received AT LEAST one address object, now assemble the BEST string
        val address = addresses[0]

        val addressParts = mutableListOf<String>()

        // 1. PRIMARY ATTEMPT: Try the full, formatted address line (most reliable)
        val fullAddressLine = address.getAddressLine(0)

        if (!fullAddressLine.isNullOrBlank() && fullAddressLine != address.countryName) {
            addressParts.add(fullAddressLine)
        }

        // 2. FALLBACK ASSEMBLY: If the full address is missing, build it from parts.
        if (addressParts.isEmpty()) {
            address.featureName?.let { addressParts.add(it) } // Building/Place name
            address.thoroughfare?.let { addressParts.add(it) } // Street name
            address.locality?.let { addressParts.add(it) } // City/Town
            address.adminArea?.let { addressParts.add(it) } // State/Province
            address.postalCode?.let { addressParts.add(it) }
        }

        // 3. FINAL CATCH: If all else fails, use the country.
        if (addressParts.isEmpty()) {
            address.countryName?.let { addressParts.add(it) }
        }

        return if (addressParts.isNotEmpty()) {
            // Success: Join all the valid parts we found
            addressParts.joinToString(", ")
        } else {
            // Last resort: we had an address object but it was totally empty
            "Address details were too sparse to display.\nLat: $latStr\nLon: $lonStr"
        }
    }
}