package com.unh.hoppin_android_app

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.LocationRestriction
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPhotoResponse
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.android.libraries.places.api.net.SearchNearbyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class RestaurantDetails(
    val name: String,
    val imageBase64: String?,
    val error: String? = null
)

private const val SEARCH_RADIUS_METERS = 1_000.0 // 5 km

private fun getPlacesClient(context: Context, apiKey: String): PlacesClient {
    if (!Places.isInitialized()) {
        Places.initialize(context.applicationContext, apiKey)
    }
    return Places.createClient(context)
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    return Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
}

suspend fun searchNearbyRestaurant(
    context: Context,
    apiKey: String,
    center: LatLng
): RestaurantDetails = withContext(Dispatchers.IO) {
    try {
        val client = getPlacesClient(context, apiKey)

        // REQUIRED in builder(...)
        val placeFields = listOf(
            Place.Field.NAME,
            Place.Field.PHOTO_METADATAS,
            Place.Field.TYPES
        )

        // Use CircularBounds (SearchNearby supports only CircularBounds)
        val restriction: LocationRestriction =
            CircularBounds.newInstance(center, SEARCH_RADIUS_METERS)

        // NOTE: builder requires (locationRestriction, placeFields)
        val req = SearchNearbyRequest.builder(restriction, placeFields)
            // Included types must be STRING type names (see Table A in docs)
            .setIncludedTypes(listOf("restaurant"))
            .setMaxResultCount(10) // 1..20
            .setRankPreference(SearchNearbyRequest.RankPreference.POPULARITY)
            .build()

        val resp: SearchNearbyResponse = Tasks.await(client.searchNearby(req))

        val place = resp.places.firstOrNull()
        if (place == null || place.name.isNullOrBlank()) {
            return@withContext RestaurantDetails(
                name = "No Nearby Restaurant Found within ${SEARCH_RADIUS_METERS.toInt()} m",
                imageBase64 = null
            )
        }

        val name = place.name!!
        val meta: PhotoMetadata? = place.photoMetadatas?.firstOrNull()
        val imageBase64 = if (meta != null) {
            val photoReq = FetchPhotoRequest.builder(meta)
                .setMaxWidth(500)
                .setMaxHeight(500)
                .build()
            val photoResp: FetchPhotoResponse = Tasks.await(client.fetchPhoto(photoReq))
            bitmapToBase64(photoResp.bitmap)
        } else null

        RestaurantDetails(name, imageBase64)
    } catch (e: Exception) {
        e.printStackTrace()
        RestaurantDetails("Error", null, "API Search Failed: ${e.message}")
    }
}
