@file:OptIn(ExperimentalMaterial3Api::class)

package com.unh.hoppin_android_app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
@Composable
fun DiscoverListScreen(
    modifier: Modifier = Modifier,
    // From NavHost (your MainActivity already passes these)
    selectedTypes: List<String> = emptyList(),
    selectedCategoryId: Int? = null,

    // Optional injections
    placesClient: PlacesClient? = null,
    center: LatLng? = null,
    radiusMeters: Int = 5_000,
    maxResults: Int = 20,

    onBack: () -> Unit = {},
    onPlaceClick: (UiPlace) -> Unit = {}
) {
    val context = LocalContext.current

    // Build a friendly, dynamic title with no changes to your subcategory file
    val dynamicTitle = remember(selectedTypes, selectedCategoryId) {
        when {
            selectedTypes.size == 1 -> readableFromType(selectedTypes.first())
            selectedTypes.size > 1   -> "Discover"
            selectedCategoryId != null -> categoryTitle(selectedCategoryId)
            else -> "Discover"
        }
    }

    // Active type filters derived from args
    val activeTypes: Set<String> = remember(selectedTypes, selectedCategoryId) {
        when {
            selectedTypes.isNotEmpty() -> selectedTypes.map { it.trim() }.toSet()
            selectedCategoryId != null -> CategoryToTypes[selectedCategoryId].orEmpty().toSet()
            else -> emptySet()
        }
    }

    val safeCenter = center ?: LatLng(41.3100, -72.9300) // New Haven fallback
    var ui by remember { mutableStateOf(ListUi(loading = true)) }
    var favorites by remember { mutableStateOf(setOf<String>()) }

    // Load places + photos
    LaunchedEffect(activeTypes, safeCenter, radiusMeters, maxResults, placesClient, context) {
        ui = ui.copy(loading = true, error = null)
        val client = placesClient ?: Places.createClient(context)
        val result = runCatching {
            loadNearbyPlacesWithPhotos(
                client = client,
                center = safeCenter,
                types = activeTypes,
                radiusMeters = radiusMeters,
                maxResults = maxResults
            )
        }
        ui = result.fold(
            onSuccess = { ListUi(loading = false, items = it) },
            onFailure = { ListUi(loading = false, error = it.message ?: "Failed to load places") }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dynamicTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        when {
            ui.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            ui.error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) { Text(ui.error!!, color = MaterialTheme.colorScheme.error) }

            else -> {
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(ui.items, key = { it.id }) { place ->
                        PlaceCardMinimal(
                            place = place.copy(isFavorite = favorites.contains(place.id)),
                            onClick = { onPlaceClick(place) },
                            onToggleFavorite = {
                                favorites = favorites.toMutableSet().apply {
                                    if (contains(place.id)) remove(place.id) else add(place.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

data class UiPlace(
    val id: String,
    val title: String,
    val photo: Bitmap?,
    val isFavorite: Boolean = false
)

data class ListUi(
    val loading: Boolean = false,
    val items: List<UiPlace> = emptyList(),
    val error: String? = null
)
val CategoryToTypes: Map<Int, List<String>> = mapOf(
    1 to listOf("tourist_attraction", "museum", "art_gallery", "park"),
    2 to listOf("restaurant", "bar", "cafe", "bakery"),
    3 to listOf("movie_theater", "night_club", "bowling_alley", "casino"),
    4 to listOf("shopping_mall", "clothing_store", "department_store", "store", "supermarket"),
    5 to listOf("spa", "lodging", "campground"),
    6 to listOf("gym", "pharmacy", "doctor", "beauty_salon"),
    7 to listOf("hospital", "police", "fire_station"),
    8 to listOf("post_office", "bank", "atm", "gas_station", "car_repair")
)
private suspend fun loadNearbyPlacesWithPhotos(
    client: PlacesClient,
    center: LatLng,
    types: Set<String>,
    radiusMeters: Int,
    maxResults: Int
): List<UiPlace> = withContext(Dispatchers.IO) {
    val locationRestriction = CircularBounds.newInstance(center, radiusMeters.toDouble())
    val placeFields = listOf(Place.Field.ID, Place.Field.NAME)

    val request = SearchNearbyRequest
        .builder(locationRestriction, placeFields)
        .setMaxResultCount(maxResults)
        .apply { if (types.isNotEmpty()) setIncludedTypes(types.toList()) }
        .build()

    val nearbyResponse = Tasks.await(client.searchNearby(request))
    val candidates = nearbyResponse.places

    candidates.mapNotNull { p ->
        val placeId = p.id ?: return@mapNotNull null

        val fetchReq = FetchPlaceRequest
            .builder(placeId, listOf(Place.Field.ID, Place.Field.NAME, Place.Field.PHOTO_METADATAS))
            .build()
        val fetched = runCatching { Tasks.await(client.fetchPlace(fetchReq)) }.getOrNull()
            ?: return@mapNotNull null

        val place = fetched.place
        val title = place.name ?: return@mapNotNull null

        val photoMeta = place.photoMetadatas?.firstOrNull()
        val bmp: Bitmap? = if (photoMeta != null) {
            val photoReq = FetchPhotoRequest.builder(photoMeta)
                .setMaxWidth(900)
                .setMaxHeight(700)
                .build()
            runCatching { Tasks.await(client.fetchPhoto(photoReq)).bitmap }.getOrNull()
        } else null

        UiPlace(id = placeId, title = title, photo = bmp)
    }
}

@Composable
private fun PlaceCardMinimal(
    place: UiPlace,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column {
            // Photo with consistent size and crop
            if (place.photo != null) {
                Image(
                    bitmap = place.photo.asImageBitmap(),
                    contentDescription = place.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFFEAEAEA)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD8D8D8))
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = place.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleFavorite) {
                    if (place.isFavorite)
                        Icon(Icons.Filled.Favorite, contentDescription = "Saved", tint = Color.Red)
                    else
                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Save")
                }
            }
        }
    }
}
private fun readableFromType(type: String): String {
    if (type.isBlank()) return "Discover"
    val special = mapOf(
        "night_club" to "Night Club",
        "bowling_alley" to "Bowling Alley",
        "shopping_mall" to "Shopping Mall",
        "art_gallery" to "Art Gallery",
        "movie_theater" to "Movie Theater",
        "gas_station" to "Gas Station",
        "car_repair" to "Car Repair",
        "tourist_attraction" to "Tourist Attraction"
    )
    special[type]?.let { return it }
    return type
        .trim()
        .split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }
}

private fun categoryTitle(categoryId: Int): String = when (categoryId) {
    1 -> "Attractions"
    2 -> "Food & Drink"
    3 -> "Entertainment"
    4 -> "Shopping"
    5 -> "Stay & Relax"
    6 -> "Wellness & Services"
    7 -> "Emergency"
    8 -> "Essentials"
    else -> "Discover"
}
