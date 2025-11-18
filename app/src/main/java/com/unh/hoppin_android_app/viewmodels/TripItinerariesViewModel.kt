package com.unh.hoppin_android_app.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unh.hoppin_android_app.TripItinerary
import com.unh.hoppin_android_app.TripItineraryRepositoryFirebase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TripItinerariesViewModel : ViewModel() {

    val itineraries: StateFlow<List<TripItinerary>> =
        TripItineraryRepositoryFirebase.userItinerariesFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun createItinerary(
        name: String,
        description: String = "",
        placeIds: List<String> = emptyList()
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                TripItineraryRepositoryFirebase.createItinerary(
                    name = name,
                    description = description,
                    placeIds = placeIds
                )
            } catch (e: Exception) {
                Log.e("TripVM", "Failed to create itinerary", e)
            }
        }
    }

    fun addPlaceToItinerary(itineraryId: String, placeId: String) {
        viewModelScope.launch {
            try {
                TripItineraryRepositoryFirebase.addPlaceToItinerary(itineraryId, placeId)
            } catch (e: Exception) {
                Log.e("TripVM", "Failed to add place to itinerary", e)
            }
        }
    }

    fun removePlaceFromItinerary(itineraryId: String, placeId: String) {
        viewModelScope.launch {
            try {
                TripItineraryRepositoryFirebase.removePlaceFromItinerary(itineraryId, placeId)
            } catch (e: Exception) {
                Log.e("TripVM", "Failed to remove place from itinerary", e)
            }
        }
    }

    fun deleteItinerary(itineraryId: String) {
        viewModelScope.launch {
            try {
                TripItineraryRepositoryFirebase.deleteItinerary(itineraryId)
            } catch (e: Exception) {
                Log.e("TripVM", "Failed to delete itinerary", e)
            }
        }
    }

    suspend fun getItinerary(itineraryId: String): TripItinerary? =
        try {
            TripItineraryRepositoryFirebase.getItinerary(itineraryId)
        } catch (e: Exception) {
            Log.e("TripVM", "Failed to get itinerary", e)
            null
        }
}
