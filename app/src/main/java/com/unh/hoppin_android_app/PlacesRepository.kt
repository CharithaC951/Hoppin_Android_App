package com.unh.hoppin_android_app.chat

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import kotlinx.coroutines.tasks.await
import java.lang.Exception

class PlacesRepository(context: Context) {

    private val placesClient = Places.createClient(context)

    suspend fun searchNearbyPlaces(
        query: String,
        userLocation: LatLng,
        radiusInMeters: Int = 5000
    ): Result<List<Place>> {

        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.RATING
        )

        val locationRestriction = CircularBounds.newInstance(userLocation, radiusInMeters.toDouble())

        val request = SearchNearbyRequest.builder(
            locationRestriction,
            placeFields
        )
            .setIncludedTypes(listOf(query.lowercase()))
            .setMaxResultCount(10)
            .build()

        return try {
            val response = placesClient.searchNearby(request).await()
            Log.d("PlacesRepository", "Successfully found ${response.places.size} places.")
            Result.success(response.places)
        } catch (e: Exception) {
            Log.e("PlacesRepository", "Error searching for places: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
