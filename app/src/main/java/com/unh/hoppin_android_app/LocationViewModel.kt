package com.unh.hoppin_android_app.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Coordinates(val latitude: Double, val longitude: Double)
data class LocationState(val coordinates: Coordinates? = null)

class LocationViewModel : ViewModel() {
    private val _locationState = MutableStateFlow(LocationState())
    val locationState: StateFlow<LocationState> = _locationState

    fun updateLocation(latitude: Double, longitude: Double) {
        _locationState.value = LocationState(Coordinates(latitude, longitude))
    }
}
