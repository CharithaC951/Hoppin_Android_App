@file:OptIn(ExperimentalMaterial3Api::class)

package com.unh.hoppin_android_app

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.unh.hoppin_android_app.FavoritesRepositoryFirebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/* ------------------------------------------------------------------ */
/* Screen                                                             */
/* ------------------------------------------------------------------ */

@Composable
fun DiscoverListScreen(
    modifier: Modifier = Modifier,
    selectedTypes: List<String> = emptyList(),
    selectedCategoryId: Int? = null,
    placesClient: PlacesClient? = null,
    center: LatLng? = null,
    radiusMeters: Int = 5_000,
    maxResults: Int = 20,
    onBack: () -> Unit = {},
    onPlaceClick: (UiPlace) -> Unit = {},
    onOpenFavorites: () -> Unit = {}
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Observe Firebase favourites
    val favIds: Set<String> by FavoritesRepositoryFirebase.favoriteIdsFlow().collectAsState(initial = emptySet())

    // Transient visual state to feel instant even if network lags slightly
    var transientAdded by remember { mutableStateOf(setOf<String>()) }

    val dynamicTitle = remember(selectedTypes, selectedCategoryId) {
        when {
            selectedTypes.size == 1 -> readableFromType(selectedTypes.first())
            selectedTypes.size > 1   -> "Discover"
            selectedCategoryId != null -> categoryTitle(selectedCategoryId)
            else -> "Discover"
        }
    }

    val activeTypes: List<String> = remember(selectedTypes, selectedCategoryId) {
        when {
            selectedTypes.isNotEmpty() -> selectedTypes.map { it.trim() }
            selectedCategoryId != null -> CategoryToTypes[selectedCategoryId].orEmpty()
            else -> emptyList()
        }
    }

    val safeCenter = center ?: LatLng(41.3100, -72.9300) // New Haven fallback
    var ui by remember { mutableStateOf(ListUi(loading = true)) }

    LaunchedEffect(activeTypes, safeCenter, radiusMeters, maxResults, placesClient, context) {
        ui = ui.copy(loading = true, error = null)
        val client = placesClient ?: Places.createClient(context)
        val result = runCatching {
            loadNearbySectionsWithPhotosAndDistance(
                client = client,
                center = safeCenter,
                typesOrdered = activeTypes,
                radiusMeters = radiusMeters,
                maxResults = maxResults
            )
        }
        ui = result.fold(
            onSuccess = { sections -> ListUi(loading = false, sections = sections) },
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
                },
                actions = {
                    IconButton(onClick = onOpenFavorites) {
                        Icon(Icons.Filled.Favorite, contentDescription = "Favourites")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { inner ->
        when {
            ui.loading -> Box(
                Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            ui.error != null -> Box(
                Modifier.fillMaxSize().padding(inner),
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
                    if (ui.sections.isEmpty()) {
                        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No near by places") } }
                    } else {
                        ui.sections.forEach { section ->
                            item(key = "header-${section.type}") {
                                Text(
                                    text = readableFromType(section.type),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (section.items.isEmpty()) {
                                item(key = "empty-${section.type}") {
                                    Text(
                                        text = "No near by ${readableFromType(section.type)}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(section.items, key = { it.id }) { place ->
                                    val isFav = favIds.contains(place.id) || transientAdded.contains(place.id)

                                    PlaceCardMinimal(
                                        place = place,
                                        isFavorited = isFav,
                                        onClick = { onPlaceClick(place) },
                                        onToggleFavorite = {
                                            // Tactile feel: quick downscale, then restore
                                            transientAdded = transientAdded + place.id
                                            scope.launch {
                                                // Write to Firebase (idempotent add)
                                                runCatching { FavoritesRepositoryFirebase.add(place.id) }
                                                // Snackbar feedback
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar("Added to favourites")
                                                // Release transient after a moment
                                                delay(1000)
                                                transientAdded = transientAdded - place.id
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* --------------------------- Data & Loading --------------------------- */

data class UiPlace(
    val id: String,
    val title: String,
    val photo: Bitmap?,
    val distanceMeters: Int? = null
)

data class UiSection(val type: String, val items: List<UiPlace>)

data class ListUi(
    val loading: Boolean = false,
    val sections: List<UiSection> = emptyList(),
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

private suspend fun loadNearbySectionsWithPhotosAndDistance(
    client: PlacesClient,
    center: LatLng,
    typesOrdered: List<String>,
    radiusMeters: Int,
    maxResults: Int
): List<UiSection> = withContext(Dispatchers.IO) {
    if (typesOrdered.isEmpty()) {
        val generic = searchNearbyOneTypeHydrated(client, center, null, radiusMeters, maxResults)
        return@withContext listOf(UiSection(type = "places", items = generic))
    }
    typesOrdered.map { t ->
        UiSection(type = t, items = searchNearbyOneTypeHydrated(client, center, t, radiusMeters, maxResults))
    }
}

private fun searchNearbyOneTypeHydrated(
    client: PlacesClient,
    center: LatLng,
    type: String?,
    radiusMeters: Int,
    maxResults: Int
): List<UiPlace> {
    val bounds = CircularBounds.newInstance(center, radiusMeters.toDouble())
    val nearbyFields = listOf(Place.Field.ID, Place.Field.NAME)
    val nearbyReq = SearchNearbyRequest
        .builder(bounds, nearbyFields)
        .setMaxResultCount(maxResults)
        .apply { if (!type.isNullOrBlank()) setIncludedTypes(listOf(type)) }
        .build()

    val nearbyResp = runCatching { Tasks.await(client.searchNearby(nearbyReq)) }.getOrNull()
        ?: return emptyList()
    val candidates = nearbyResp.places.orEmpty()

    return candidates.mapNotNull { p ->
        val placeId = p.id ?: return@mapNotNull null
        val fetched = runCatching {
            val req = FetchPlaceRequest.builder(
                placeId,
                listOf(Place.Field.ID, Place.Field.NAME, Place.Field.PHOTO_METADATAS, Place.Field.LAT_LNG)
            ).build()
            Tasks.await(client.fetchPlace(req)).place
        }.getOrNull() ?: return@mapNotNull null

        val title = fetched.name ?: return@mapNotNull null
        val meta: PhotoMetadata? = fetched.photoMetadatas?.firstOrNull()
        val bmp: Bitmap? = if (meta != null) {
            runCatching {
                val preq = FetchPhotoRequest.builder(meta).setMaxWidth(1280).setMaxHeight(720).build()
                Tasks.await(client.fetchPhoto(preq)).bitmap
            }.getOrNull()
        } else null

        val d = fetched.latLng?.let { ll ->
            distanceMeters(center.latitude, center.longitude, ll.latitude, ll.longitude).roundToInt()
        }

        UiPlace(id = placeId, title = title, photo = bmp, distanceMeters = d)
    }.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
}

/* --------------------------- Card UI --------------------------- */

@Composable
private fun PlaceCardMinimal(
    place: UiPlace,
    isFavorited: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (pressed) 0.88f else 1f, label = "fav-press")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column {
            if (place.photo != null) {
                Image(
                    bitmap = place.photo.asImageBitmap(),
                    contentDescription = place.title,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp).background(Color(0xFFEAEAEA)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFD8D8D8)))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(place.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    place.distanceMeters?.let { Text(formatDistance(it), style = MaterialTheme.typography.bodySmall) }
                }
                IconButton(
                    onClick = {
                        pressed = true
                        onToggleFavorite()
                    },
                    modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
                ) {
                    if (isFavorited)
                        Icon(Icons.Filled.Favorite, contentDescription = "Added", tint = Color.Red)
                    else
                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Save")
                }
            }
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) { delay(120); pressed = false }
    }
}

/* --------------------------- Titles & Utils --------------------------- */

private fun readableFromType(type: String): String {
    if (type.isBlank()) return "Discover"
    val special = mapOf(
        "night_club" to "Night Club", "bowling_alley" to "Bowling Alley", "shopping_mall" to "Shopping Mall",
        "art_gallery" to "Art Gallery", "movie_theater" to "Movie Theater", "gas_station" to "Gas Station",
        "car_repair" to "Car Repair", "tourist_attraction" to "Tourist Attraction"
    )
    return special[type] ?: type.split('_').filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }
}

private fun categoryTitle(categoryId: Int): String = when (categoryId) {
    1 -> "Attractions"; 2 -> "Food & Drink"; 3 -> "Entertainment"; 4 -> "Shopping"
    5 -> "Stay & Relax"; 6 -> "Wellness & Services"; 7 -> "Emergency"; 8 -> "Essentials"; else -> "Discover"
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat/2)*sin(dLat/2) + cos(Math.toRadians(lat1))*cos(Math.toRadians(lat2))*sin(dLon/2)*sin(dLon/2)
    return R * 2 * atan2(sqrt(a), sqrt(1-a))
}

private fun formatDistance(m: Int): String = if (m >= 1000) String.format("%.1f km away", m / 1000.0) else "$m m away"
