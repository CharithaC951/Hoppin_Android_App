package com.unh.hoppin_android_app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.LocationRestriction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission") // All location calls are gated by permission checks
@Composable
fun MapScreen(
    mapsApiKey: String,
    navController: NavController
) {
    // ---- Permissions ----
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    LaunchedEffect(Unit) {
        if (!locationPermissions.allPermissionsGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // ---- State / Services ----
    val context = LocalContext.current
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    var userLatLng by remember { mutableStateOf<LatLng?>(null) }
    var pins by remember { mutableStateOf<List<PlacePin>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val defaultCenter = LatLng(41.3083, -72.9279) // New Haven fallback
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, 14f)
    }

    // Explore selection (Restaurants / Entertainment / Tourism)
    var selected by remember { mutableStateOf(ExploreType.RESTAURANTS) }

    // ---- Acquire device location once permissions are granted ----
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (!locationPermissions.allPermissionsGranted) return@LaunchedEffect

        val current = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            ?: fused.lastLocation.await()

        val center = if (current != null) LatLng(current.latitude, current.longitude) else defaultCenter
        userLatLng = center
        cameraPositionState.position = CameraPosition.fromLatLngZoom(center, 15f)
    }

    // ---- Fetch nearby places when selection or location changes ----
    LaunchedEffect(selected, userLatLng) {
        val center = userLatLng ?: return@LaunchedEffect
        loading = true
        errorText = null
        try {
            pins = fetchPlacesByExploreType(
                context = context,
                apiKey = mapsApiKey,
                center = center,
                exploreType = selected,
                radiusMeters = 5_000.0,   // 5 km
                maxCount = 10             // Top 10
            )
        } catch (e: Exception) {
            e.printStackTrace()
            errorText = "Failed to load places: ${e.message ?: "Unknown error"}"
            pins = emptyList()
        } finally {
            loading = false
        }
    }

    // ---- UI ----
    Box(Modifier.fillMaxSize()) {
        val mapProps = MapProperties(
            isMyLocationEnabled = locationPermissions.allPermissionsGranted
        )
        val uiSettings = MapUiSettings(
            compassEnabled = true,
            myLocationButtonEnabled = locationPermissions.allPermissionsGranted,
            zoomControlsEnabled = false
        )

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProps,
            uiSettings = uiSettings
        ) {
            // Optional explicit user marker; blue dot is shown by My Location layer anyway
            userLatLng?.let {
                Marker(state = MarkerState(position = it), title = "You are here")
            }

            pins.forEach { pin ->
                Marker(
                    state = MarkerState(position = pin.position),
                    title = pin.title,
                    snippet = pin.subtitle
                )
            }
        }

        // Top overlay: filter chips
        ExploreBar(
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
        )

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }

        errorText?.let {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/* --------------------------- UI: Explore Bar --------------------------- */

@Composable
private fun ExploreBar(
    selected: ExploreType,
    onSelect: (ExploreType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCCFFFFFF) // translucent white
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selected == ExploreType.RESTAURANTS,
                onClick = { onSelect(ExploreType.RESTAURANTS) },
                label = { Text("Restaurants") }
            )
            FilterChip(
                selected = selected == ExploreType.ENTERTAINMENT,
                onClick = { onSelect(ExploreType.ENTERTAINMENT) },
                label = { Text("Entertainment") }
            )
            FilterChip(
                selected = selected == ExploreType.TOURISM,
                onClick = { onSelect(ExploreType.TOURISM) },
                label = { Text("Tourism") }
            )
        }
    }
}

/* --------------------------- Data & Fetch --------------------------- */

private enum class ExploreType {
    RESTAURANTS, ENTERTAINMENT, TOURISM
}

private data class PlacePin(
    val position: LatLng,
    val title: String,
    val subtitle: String? = null,
    val photo: Bitmap? = null // reserved for custom markers
)

private fun getPlacesClient(context: android.content.Context, apiKey: String): PlacesClient {
    if (!Places.isInitialized()) {
        Places.initialize(context.applicationContext, apiKey)
    }
    return Places.createClient(context)
}

private suspend fun fetchPlacesByExploreType(
    context: android.content.Context,
    apiKey: String,
    center: LatLng,
    exploreType: ExploreType,
    radiusMeters: Double,
    maxCount: Int
): List<PlacePin> = withContext(Dispatchers.IO) {
    val client = getPlacesClient(context, apiKey)

    val types = when (exploreType) {
        ExploreType.RESTAURANTS   -> listOf("restaurant")
        ExploreType.ENTERTAINMENT -> listOf("movie_theater")
        ExploreType.TOURISM       -> listOf("tourist_attraction")
    }

    val fields = listOf(
        Place.Field.NAME,
        Place.Field.LAT_LNG,
        Place.Field.RATING,
        Place.Field.USER_RATINGS_TOTAL
    )

    val restriction: LocationRestriction = CircularBounds.newInstance(center, radiusMeters)

    val request = SearchNearbyRequest.builder(restriction, fields)
        .setIncludedTypes(types)
        .setMaxResultCount(maxCount)
        .setRankPreference(SearchNearbyRequest.RankPreference.POPULARITY)
        .build()

    try {
        val resp = client.searchNearby(request).await()
        resp.places
            .filter { it.latLng != null && !it.name.isNullOrBlank() }
            .map { p ->
                val rating = p.rating?.let { r ->
                    val cnt = p.userRatingsTotal ?: 0
                    "⭐ ${"%.1f".format(r)} ($cnt)"
                }
                PlacePin(
                    position = p.latLng!!,
                    title = p.name!!,
                    subtitle = rating
                )
            }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}
