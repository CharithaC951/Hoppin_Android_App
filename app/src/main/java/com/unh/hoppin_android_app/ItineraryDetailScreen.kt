@file:OptIn(ExperimentalMaterial3Api::class)

package com.unh.hoppin_android_app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unh.hoppin_android_app.viewmodels.TripItinerariesViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

@Composable
fun ItineraryDetailScreen(
    itineraryId: String,
    onBack: () -> Unit,
    onPlaceClick: (String) -> Unit      // 🔥 NEW PARAMETER
) {
    val vm: TripItinerariesViewModel = viewModel()
    val itineraries by vm.itineraries.collectAsState()

    val context = LocalContext.current
    val itinerary = itineraries.find { it.id == itineraryId }

    var placesUi by remember { mutableStateOf<List<UiPlace>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val center = LatLng(41.3100, -72.9300)

    LaunchedEffect(itinerary?.placeIds) {
        val ids = itinerary?.placeIds ?: emptyList()
        if (ids.isEmpty()) {
            placesUi = emptyList()
            return@LaunchedEffect
        }

        loading = true
        error = null

        try {
            val client = Places.createClient(context)
            val hydrated = hydrateTripPlaces(client, ids, center)
            placesUi = hydrated
            loading = false
        } catch (e: Exception) {
            loading = false
            error = e.localizedMessage ?: "Failed to load trip places"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(itinerary?.name ?: "Trip Itinerary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                    )
                }
            ) { padding ->

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {

                    when {
                        itinerary == null -> Text(
                            "This itinerary could not be found.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center)
                        )

                        loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                        error != null -> Text(
                            error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )

                        placesUi.isEmpty() -> Text(
                            "No places added yet.\nUse \"Add to Trip\" from a place screen.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center)
                        )

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {

                                item {
                                    Text(
                                        itinerary.name,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (itinerary.description.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(itinerary.description)
                                        Spacer(Modifier.height(12.dp))
                                    }
                                }

                                items(placesUi, key = { it.id }) { place ->
                                    PlaceCardMinimal(
                                        place = place,
                                        isFavorited = false,
                                        onClick = { onPlaceClick(place.id) },   // 🔥 OPEN PLACE DETAILS
                                        onToggleFavorite = { }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

                /* SAME HELPER FUNCTIONS AS BEFORE — unchanged */
                private suspend fun hydrateTripPlaces(
            client: com.google.android.libraries.places.api.net.PlacesClient,
            placeIds: List<String>,
            center: LatLng
        ): List<UiPlace> = withContext(Dispatchers.IO) {

            val result = mutableListOf<UiPlace>()

            for (id in placeIds) {
                try {
                    val fields = listOf(
                        Place.Field.ID,
                        Place.Field.NAME,
                        Place.Field.PHOTO_METADATAS,
                        Place.Field.LAT_LNG
                    )

                    val req = FetchPlaceRequest.builder(id, fields).build()
                    val place = Tasks.await(client.fetchPlace(req)).place ?: continue

                    val title = place.name ?: continue

                    val meta: PhotoMetadata? = place.photoMetadatas?.firstOrNull()
                    val bmp: Bitmap? = if (meta != null) {
                        runCatching {
                            val preq = FetchPhotoRequest.builder(meta)
                                .setMaxWidth(1280)
                                .setMaxHeight(720)
                                .build()
                            Tasks.await(client.fetchPhoto(preq)).bitmap
                        }.getOrNull()
                    } else null

                    val distanceM = place.latLng?.let {
                        computeDistanceMeters(center, it).roundToInt()
                    }

                    result += UiPlace(
                        id = id,
                        title = title,
                        photo = bmp,
                        distanceMeters = distanceM,
                        rating = null,
                        isOpenNow = null
                    )
                } catch (_: Exception) {
                    continue
                }
            }

            result
        }

                private fun computeDistanceMeters(a: LatLng, b: LatLng): Double {
            val R = 6371000.0
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)

            val x = sin(dLat / 2) * sin(dLat / 2) +
                    sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)

            return R * 2 * atan2(sqrt(x), sqrt(1 - x))
        }
