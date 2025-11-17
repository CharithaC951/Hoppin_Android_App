package com.unh.hoppin_android_app

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth

class VisitTracker(
    private val context: Context,
    private val fusedClient: FusedLocationProviderClient,
    private val placesClient: PlacesClient
) {
    fun start() { /* request location updates, detect dwell, call CategoryRewardService */ }
    fun stop()  { /* remove location updates */ }
}