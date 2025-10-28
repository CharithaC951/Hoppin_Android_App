package com.unh.hoppin_android_app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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

// -----------------------------
//  Public Data Types
// -----------------------------
enum class ExploreType { RESTAURANTS, ENTERTAINMENT, TOURISM, SHOPPING, EMERGENCY }

data class PlacePin(
    val position: LatLng,
    val title: String,
    val subtitle: String? = null,
    val photo: Bitmap? = null
)

// -----------------------------
//  Main Composable
// -----------------------------
@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    mapsApiKey: String,
    navController: NavController
) {
    // Permissions
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

    // State / Services
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

    var selected by remember { mutableStateOf(ExploreType.RESTAURANTS) }

    // Acquire device location
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (!locationPermissions.allPermissionsGranted) return@LaunchedEffect
        val current = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            ?: fused.lastLocation.await()
        val center = if (current != null) LatLng(current.latitude, current.longitude) else defaultCenter
        userLatLng = center
        cameraPositionState.position = CameraPosition.fromLatLngZoom(center, 15f)
    }

    // Fetch nearby places whenever selection or location changes
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
                radiusMeters = 20.0 * 1609.344, // 20 miles ≈ 32,187 m
                maxCount = 10
            )
        } catch (e: Exception) {
            e.printStackTrace()
            errorText = "Failed to load places: ${e.message ?: "Unknown error"}"
            pins = emptyList()
        } finally {
            loading = false
        }
    }

    // UI
    Box(Modifier.fillMaxSize()) {
        val mapProps = MapProperties(
            isMyLocationEnabled = locationPermissions.allPermissionsGranted
        )
        val uiSettings = MapUiSettings(
            compassEnabled = true,
            myLocationButtonEnabled = locationPermissions.allPermissionsGranted,
            zoomControlsEnabled = true // show native zoom controls
        )

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProps,
            uiSettings = uiSettings
        ) {
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

        // Wrapping, elevated chip bar
        ExploreBar(
            selected = selected,
            onSelect = { selected = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )

        // Custom zoom control (no CameraUpdateFactory required)
        ZoomControls(
            onZoomIn = {
                val pos = cameraPositionState.position
                val target = pos.target
                val newZoom = (pos.zoom + 1f).coerceAtMost(21f)
                cameraPositionState.position = CameraPosition.fromLatLngZoom(target, newZoom)
            },
            onZoomOut = {
                val pos = cameraPositionState.position
                val target = pos.target
                val newZoom = (pos.zoom - 1f).coerceAtLeast(2f)
                cameraPositionState.position = CameraPosition.fromLatLngZoom(target, newZoom)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 24.dp)
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

// -----------------------------
//  Explore Bar UI
// -----------------------------
@Composable
private fun ExploreBar(
    selected: ExploreType,
    onSelect: (ExploreType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCCFFFFFF),
        tonalElevation = 6.dp,
        shadowElevation = 2.dp
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExploreChip(
                label = "Restaurants",
                selected = selected == ExploreType.RESTAURANTS,
                icon = { Icon(Icons.Filled.LocalDining, contentDescription = null) },
                onClick = { onSelect(ExploreType.RESTAURANTS) }
            )
            ExploreChip(
                label = "Entertainment",
                selected = selected == ExploreType.ENTERTAINMENT,
                icon = { Icon(Icons.Filled.Movie, contentDescription = null) },
                onClick = { onSelect(ExploreType.ENTERTAINMENT) }
            )
            ExploreChip(
                label = "Tourism",
                selected = selected == ExploreType.TOURISM,
                icon = { Icon(Icons.Filled.Map, contentDescription = null) },
                onClick = { onSelect(ExploreType.TOURISM) }
            )
            ExploreChip(
                label = "Shopping",
                selected = selected == ExploreType.SHOPPING,
                icon = { Icon(Icons.Filled.ShoppingBag, contentDescription = null) },
                onClick = { onSelect(ExploreType.SHOPPING) }
            )
            ExploreChip(
                label = "Emergency",
                selected = selected == ExploreType.EMERGENCY,
                icon = { Icon(Icons.Filled.LocalHospital, contentDescription = null) },
                onClick = { onSelect(ExploreType.EMERGENCY) }
            )
        }
    }
}

@Composable
private fun FlowRowScope.ExploreChip(
    label: String,
    selected: Boolean,
    icon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon?.let { ic -> { ic() } },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

// -----------------------------
//  Zoom Controls
// -----------------------------
@Composable
private fun ZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 2.dp,
        color = Color(0xCCFFFFFF)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onZoomIn) { Icon(Icons.Filled.Add, contentDescription = "Zoom in") }
            Divider(modifier = Modifier.width(40.dp))
            IconButton(onClick = onZoomOut) { Icon(Icons.Filled.Remove, contentDescription = "Zoom out") }
        }
    }
}

// -----------------------------
//  Fetch Logic
// -----------------------------
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

    // Expanded type groups per category
    val types: List<String> = when (exploreType) {
        // Restaurants: restaurants, cafes, bars (incl. pubs/coffee shops)
        ExploreType.RESTAURANTS -> listOf(
            "restaurant", "cafe", "coffee_shop", "bar", "pub"
        )
        // Entertainment: movie theaters, nightlife, play areas/arcades/bowling
        ExploreType.ENTERTAINMENT -> listOf(
            "movie_theater", "night_club", "bar", "amusement_center", "bowling_alley", "video_arcade", "playground"
        )
        // Tourism: beaches, local attractions, parks/gardens/museums
        ExploreType.TOURISM -> listOf(
            "tourist_attraction", "park", "national_park", "beach", "botanical_garden", "museum", "art_gallery"
        )
        // Shopping: malls + groceries + general retail
        ExploreType.SHOPPING -> listOf(
            "shopping_mall", "supermarket", "grocery_store", "department_store", "convenience_store", "market"
        )
        // Emergency: fire, police, hospital
        ExploreType.EMERGENCY -> listOf(
            "fire_station", "police", "hospital"
        )
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
