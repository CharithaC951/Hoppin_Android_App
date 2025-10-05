import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.Locale

// =================================================================
// 1. CORE LOGIC AND DATA STRUCTURES
// =================================================================

/**
 * Data class to hold the location status and coordinates.
 */
data class LocationData(
    val status: String,
    val coordinates: LatLng? = null
)

/**
 * A stateful Composable that handles location fetching and reverse geocoding automatically.
 *
 * @param fusedLocationClient The FusedLocationProviderClient instance.
 * @return A State<LocationData> containing the current status and coordinates.
 */
@Composable
fun CurrentAddressStatusText(
    fusedLocationClient: FusedLocationProviderClient
): State<LocationData> {

    val context = LocalContext.current
    val defaultLocation = remember { LatLng(34.0522, -118.2437) }

    // The state that will hold the current status and coordinates
    var locationData by remember {
        mutableStateOf(LocationData("Starting automatic address fetch...", defaultLocation))
    }
    val TAG = "ADDRESS_FETCHER"

    // LaunchedEffect(Unit) ensures this code block runs exactly once when the Composable is first launched.
    LaunchedEffect(Unit) {

        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationData = LocationData("PERMISSION REQUIRED. Grant location access and restart.", defaultLocation)
            return@LaunchedEffect
        }

        locationData = LocationData("Fetching coordinates...", locationData.coordinates)

        // Step 1: Get Latitude and Longitude
        try {
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).await()

            if (location != null) {
                val coords = LatLng(location.latitude, location.longitude)

                locationData = LocationData("Lat: ${location.latitude}, Lon: ${location.longitude}\nReverse geocoding...", coords)

                // Step 2: Reverse Geocode (run on IO thread)
                launch(Dispatchers.IO) {
                    reverseGeocode(context, location) { resultStatus ->
                        // Update the state on the Main thread with the final address
                        locationData = LocationData(resultStatus, coords)
                    }
                }
            } else {
                locationData = LocationData("Could not find current location (null). Check device settings.", locationData.coordinates)
            }
        } catch (e: Exception) {
            locationData = LocationData("Error fetching location: ${e.message}", locationData.coordinates)
        }
    }

    // Return the stable state object for the UI to observe
    return rememberUpdatedState(locationData)
}

/**
 * Converts coordinates to address using Geocoder and prints the result to logcat.
 */
private fun reverseGeocode(
    context: Context,
    location: Location,
    onResult: (String) -> Unit
) {
    val TAG = "ADDRESS_FETCHER"
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

        val result = if (!addresses.isNullOrEmpty()) {
            val address = addresses[0]
            val fullAddress = buildAddressString(address)

            // Log the address as requested
            Log.d(TAG, "--- SUCCESS: Nearby Address Found ---")
            Log.d(TAG, fullAddress)

            fullAddress
        } else {
            "No address found for these coordinates."
        }

        onResult(result)

    } catch (e: IOException) {
        onResult("Geocoder service not available or network error.")
    }
}

private fun buildAddressString(address: android.location.Address): String {
    val addressBuilder = StringBuilder()
    for (i in 0..address.maxAddressLineIndex) {
        addressBuilder.append(address.getAddressLine(i))
        if (i < address.maxAddressLineIndex) addressBuilder.append("\n")
    }
    return addressBuilder.toString()
}


// =================================================================
// 2. COMPOSABLE SCREEN (UI)
// =================================================================

/**
 * Simple Composable screen that displays the fetch status.
 */
@Composable
fun AddressAppScreen() {
    val context = LocalContext.current

    // Initialize FusedLocationProviderClient once
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Get the live location data from the stateful composable function
    val locationData by CurrentAddressStatusText(fusedLocationClient = fusedLocationClient)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Automatic Address Fetcher",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Display the live status text
        Text(
            text = locationData.status,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 32.dp),
            textAlign = TextAlign.Center
        )

        // The button has been removed, fetching is now automatic via LaunchedEffect(Unit)

        Text(
            text = "Fetching address automatically...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}