package com.unh.hoppin_android_app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.util.Log
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import java.util.Locale

data class LocationData(
    val status: String,
    val coordinates: LatLng? = null
)

class LocationViewModel : ViewModel() {
    private val defaultLocation = LatLng(34.0522, -118.2437)

    private val _locationState = MutableStateFlow(
        LocationData("Initializing...", defaultLocation)
    )
    val locationState: StateFlow<LocationData> = _locationState.asStateFlow()

    fun fetchCurrentLocationAndAddress(context: Context, fusedLocationClient: FusedLocationProviderClient) {
        val TAG = "LOCATION_VIEWMODEL"

        // Use viewModelScope for coroutine management
        viewModelScope.launch {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                _locationState.value = LocationData("PERMISSION REQUIRED. Grant location access.", defaultLocation)
                return@launch
            }

            _locationState.value = LocationData("Fetching coordinates...", defaultLocation)

            // Step 1: Get Latitude and Longitude
            try {
                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).await()

                if (location != null) {
                    val coords = LatLng(location.latitude, location.longitude)

                    _locationState.value = LocationData("Lat: ${coords.latitude}, Lon: ${coords.longitude}\nReverse geocoding...", coords)

                    // Step 2: Reverse Geocode (run on IO thread)
                    launch(Dispatchers.IO) {
                        val addressStatus = reverseGeocode(context, location)
                        // Update the StateFlow on the Main thread (via viewModelScope)
                        _locationState.value = LocationData(addressStatus, coords)
                    }
                } else {
                    _locationState.value = LocationData("Could not find current location (null). Check settings.", defaultLocation)
                }
            } catch (e: Exception) {
                _locationState.value = LocationData("Error fetching location: ${e.message}", defaultLocation)
            }
        }
    }

    // Helper function moved from the previous file
    private suspend fun reverseGeocode(
        context: Context,
        location: Location
    ): String {
        val TAG = "LOCATION_VIEWMODEL"
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val fullAddress = buildAddressString(address)
                Log.d(TAG, "--- SUCCESS: Nearby Address Found ---")
                Log.d(TAG, fullAddress)
                fullAddress
            } else {
                "No address found for these coordinates."
            }
        } catch (e: IOException) {
            "Geocoder service not available or network error."
        }
    }
    
    private fun buildAddressString(address: Address): String {

        val street = address.thoroughfare?.trim() ?: ""

        val city = address.locality?.trim() ?: ""

        val streetLine = if (street.isNotEmpty()) "$street\n" else ""
        val cityLine = if (city.isNotEmpty()) city else "Location found"


        return if (streetLine.isEmpty() && cityLine == "Location found") {

            "Coordinates found"
        } else {

            "$streetLine$cityLine"
        }
    }
}