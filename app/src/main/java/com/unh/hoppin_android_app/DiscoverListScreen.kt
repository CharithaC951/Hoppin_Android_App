@file:OptIn(ExperimentalMaterial3Api::class)

package com.unh.hoppin_android_app

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.google.android.libraries.places.api.model.DayOfWeek
import com.google.android.libraries.places.api.model.LocalTime
import com.google.android.libraries.places.api.model.OpeningHours
import com.google.android.libraries.places.api.model.Period
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Keep photos small for speed
private const val PHOTO_MAX_WIDTH = 640
private const val PHOTO_MAX_HEIGHT = 400

/* ------------------------------------------------------------------ */
/* Filters                                                            */
/* ------------------------------------------------------------------ */

enum class SortOption { NEAREST, FARTHEST }

data class DiscoverFilters(
    val sort: SortOption = SortOption.NEAREST,
    val openNow: Boolean = false,     // client-side
    val minRating: Float? = null,     // no default rating filter
    val onlyWithPhoto: Boolean = false,
    val onlyFavorites: Boolean = false
)

/* ------------------------------------------------------------------ */
/* Simple in-memory cache for nearby sections                         */
/* ------------------------------------------------------------------ */

private data class SectionsCacheKey(
    val latBucket: Int,
    val lngBucket: Int,
    val radius: Int,
    val maxResults: Int,
    val typesSignature: String
)

private object DiscoverNearbyCache {
    private val cache = mutableMapOf<SectionsCacheKey, List<UiSection>>()

    fun get(key: SectionsCacheKey): List<UiSection>? = cache[key]

    fun put(key: SectionsCacheKey, sections: List<UiSection>) {
        cache[key] = sections
    }

    fun clear() {
        cache.clear()
    }
}

// Default set used for prefetch
val DefaultPrefetchTypes: List<String> = listOf(
    "restaurant",
    "cafe",
    "bar",
    "bakery",
    "tourist_attraction",
    "museum",
    "park",
    "shopping_mall",
    "clothing_store"
)

/**
 * Called from MainActivity once we know the user's location.
 * Warms cache with metadata (no photos).
 */
suspend fun prefetchNearbyForCenter(
    client: PlacesClient,
    center: LatLng,
    radiusMeters: Int = 2_000,
    maxResults: Int = 12        // slightly smaller for speed
) {
    loadNearbySectionsWithPhotosDistanceRatingOpenNow(
        client = client,
        center = center,
        typesOrdered = DefaultPrefetchTypes,
        radiusMeters = radiusMeters,
        maxResults = maxResults
    )
}

/* ------------------------------------------------------------------ */
/* Screen                                                             */
/* ------------------------------------------------------------------ */

@Composable
fun DiscoverListScreen(
    modifier: Modifier = Modifier,
    selectedTypes: List<String> = emptyList(),
    selectedCategoryId: Int? = null,
    placesClient: PlacesClient? = null,
    center: LatLng? = null,                 // GPS location passed from MainActivity
    radiusMeters: Int = 1_500,              // < 1 mile (~0.93 mi)
    maxResults: Int = 10,                   // fewer places per type for faster load
    onBack: () -> Unit = {},
    onPlaceClick: (UiPlace) -> Unit = {},
    onOpenFavorites: () -> Unit = {}
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val favIds: Set<String> by FavoritesRepositoryFirebase
        .favoriteIdsFlow()
        .collectAsState(initial = emptySet())

    var transientAdded by remember { mutableStateOf(setOf<String>()) }

    // start with no filters enabled
    var filters by remember {
        mutableStateOf(DiscoverFilters())
    }

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

    var ui by remember { mutableStateOf(ListUi(loading = true)) }

    LaunchedEffect(activeTypes, center, radiusMeters, maxResults, placesClient, context) {
        ui = ui.copy(loading = true, error = null)

        val currentCenter = center
        if (currentCenter == null) {
            // No fallback – only GPS. If null, show error.
            ui = ListUi(
                loading = false,
                sections = emptyList(),
                error = "Location unavailable"
            )
            return@LaunchedEffect
        }

        val client = placesClient ?: Places.createClient(context)
        val result = runCatching {
            loadNearbySectionsWithPhotosDistanceRatingOpenNow(
                client = client,
                center = currentCenter,
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFFE082),   // warm yellow
                    titleContentColor = Color(0xFF4E342E) // dark brown for contrast
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { inner ->
        when {
            ui.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            ui.error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
            }

            else -> {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(horizontal = 16.dp)
                ) {
                    // Filter bar
                    FilterBar(
                        filters = filters,
                        onChange = { filters = it }
                    )

                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (ui.sections.isEmpty()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No near by places")
                                }
                            }
                        } else {
                            ui.sections.forEach { rawSection ->
                                val section = applyFiltersToSection(rawSection, favIds, filters)

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
                                        val isFav =
                                            favIds.contains(place.id) || transientAdded.contains(place.id)

                                        PlaceCardMinimal(
                                            place = place,
                                            photo = cachedPhoto,
                                            client = client,
                                            isVisible = isVisible,
                                            onPhotoLoaded = { bmp ->
                                                photoCache[place.id] = bmp
                                            },
                                            isFavorited = isFav,
                                            onClick = { onPlaceClick(place) },
                                            onToggleFavorite = {
                                                transientAdded = transientAdded + place.id
                                                scope.launch {
                                                    runCatching {
                                                        FavoritesRepositoryFirebase.add(place.id)
                                                    }
                                                    snackbarHostState.currentSnackbarData?.dismiss()
                                                    snackbarHostState.showSnackbar("Added to favourites")
                                                    kotlinx.coroutines.delay(1000)
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
}

/* --------------------------- Filter UI --------------------------- */

@Composable
private fun FilterBar(
    filters: DiscoverFilters,
    onChange: (DiscoverFilters) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFF8E1) // light warm background
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val selectedColor = Color(0xFFFFB300)   // amber
            val unselectedColor = Color(0x26FFA000) // faint orange tint

            @Composable
            fun chipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
                selectedContainerColor = if (selected) selectedColor else Color.Transparent,
                selectedLabelColor = Color.Black,
                containerColor = if (!selected) unselectedColor else selectedColor,
                labelColor = Color.Black
            )

            item {
                FilterChip(
                    selected = filters.sort == SortOption.NEAREST,
                    onClick = {
                        onChange(
                            filters.copy(
                                sort = if (filters.sort == SortOption.NEAREST)
                                    SortOption.FARTHEST
                                else
                                    SortOption.NEAREST
                            )
                        )
                    },
                    label = {
                        Text(
                            if (filters.sort == SortOption.NEAREST)
                                "Nearest"
                            else
                                "Farthest"
                        )
                    },
                    colors = chipColors(filters.sort == SortOption.NEAREST)
                )
            }
            item {
                FilterChip(
                    selected = filters.openNow,
                    onClick = { onChange(filters.copy(openNow = !filters.openNow)) },
                    label = { Text("Open now") },
                    colors = chipColors(filters.openNow)
                )
            }
            item {
                FilterChip(
                    selected = (filters.minRating ?: 0f) >= 4.0f,
                    onClick = {
                        onChange(
                            filters.copy(
                                minRating = if ((filters.minRating ?: 0f) >= 4.0f)
                                    null
                                else
                                    4.0f
                            )
                        )
                    },
                    label = { Text("4.0+") },
                    colors = chipColors((filters.minRating ?: 0f) >= 4.0f)
                )
            }
            item {
                FilterChip(
                    selected = filters.onlyFavorites,
                    onClick = { onChange(filters.copy(onlyFavorites = !filters.onlyFavorites)) },
                    label = { Text("Favorites") },
                    colors = chipColors(filters.onlyFavorites)
                )
            }
        }
    }
}

/* --------------------------- Data & Loading --------------------------- */

data class UiPlace(
    val id: String,
    val title: String,
    val photo: Bitmap?,                         // kept for compatibility
    val distanceMeters: Int? = null,
    val rating: Float? = null,
    val isOpenNow: Boolean? = null,
    val photoMetadata: PhotoMetadata? = null    // used for lazy photo loading
)

data class UiSection(val type: String, val items: List<UiPlace>)

data class ListUi(
    val loading: Boolean = false,
    val sections: List<UiSection> = emptyList(),
    val error: String? = null
)

/**
 * Category mapping – updated Shopping (4) to also include "grocery_or_supermarket"
 * so things like Stop & Shop can show up under that category.
 */
val CategoryToTypes: Map<Int, List<String>> = mapOf(
    1 to listOf("tourist_attraction", "museum", "art_gallery", "park"),
    2 to listOf("restaurant", "bar", "cafe", "bakery"),
    3 to listOf("movie_theater", "night_club", "bowling_alley", "casino"),
    4 to listOf( // Shopping
        "shopping_mall",
        "clothing_store",
        "department_store",
        "store",
        "supermarket",
        "grocery_or_supermarket" // 👈 added for grocery chains like Stop & Shop
    ),
    5 to listOf("spa", "lodging", "campground"),
    6 to listOf("gym", "pharmacy", "doctor", "beauty_salon"),
    7 to listOf("hospital", "police", "fire_station"),
    8 to listOf("post_office", "bank", "atm", "gas_station", "car_repair")
)

/* --------------------------- Loading with cache + parallelism ------- */

suspend fun loadNearbySectionsWithPhotosDistanceRatingOpenNow(
    client: PlacesClient,
    center: LatLng,
    typesOrdered: List<String>,
    radiusMeters: Int,
    maxResults: Int
): List<UiSection> = withContext(Dispatchers.IO) {
    if (typesOrdered.isEmpty()) {
        val generic =
            searchNearbyOneTypeHydrated(client, center, null, radiusMeters, maxResults)
        return@withContext listOf(UiSection(type = "places", items = generic))
    }

    // 2) Fetch if not cached (metadata only, no photos)
    val sections: List<UiSection> = if (typesOrdered.isEmpty()) {
        val generic = searchNearbyOneTypeHydrated(
            client = client,
            center = center,
            type = null,
            radiusMeters = radiusMeters,
            maxResults = maxResults
        )
        listOf(UiSection(type = "places", items = generic))
    } else {
        coroutineScope {
            typesOrdered.map { t ->
                async {
                    val items = searchNearbyOneTypeHydrated(
                        client = client,
                        center = center,
                        type = t,
                        radiusMeters = radiusMeters,
                        maxResults = maxResults
                    )
                    UiSection(type = t, items = items)
                }
            }.awaitAll()
        }
    }

    // 3) Save to cache
    DiscoverNearbyCache.put(key, sections)
    return@withContext sections
}

/* --------------------------- Search + hydrate (no photos) ---------- */

private suspend fun searchNearbyOneTypeHydrated(
    client: PlacesClient,
    center: LatLng,
    type: String?,
    radiusMeters: Int,
    maxResults: Int
): List<UiPlace> = withContext(Dispatchers.IO) {
    val bounds = CircularBounds.newInstance(center, radiusMeters.toDouble())
    val nearbyFields = listOf(Place.Field.ID, Place.Field.NAME)
    val nearbyReq = SearchNearbyRequest
        .builder(bounds, nearbyFields)
        .setMaxResultCount(maxResults)
        .apply {
            if (!type.isNullOrBlank()) setIncludedTypes(listOf(type))
        }
        .build()

    val nearbyResp = runCatching {
        Tasks.await(client.searchNearby(nearbyReq))
    }.getOrNull() ?: return@withContext emptyList()

    val candidates = nearbyResp.places.orEmpty()
    if (candidates.isEmpty()) return@withContext emptyList()

    // Hydrate metadata in parallel (ID, title, rating, openNow, photoMetadata)
    return@withContext coroutineScope {
        val deferreds = candidates.mapNotNull { p ->
            val placeId = p.id ?: return@mapNotNull null
            async(Dispatchers.IO) {
                val fetched = runCatching {
                    val req = FetchPlaceRequest.builder(
                        placeId,
                        listOf(
                            Place.Field.ID,
                            Place.Field.NAME,
                            Place.Field.PHOTO_METADATAS,
                            Place.Field.LAT_LNG,
                            Place.Field.RATING,
                            Place.Field.OPENING_HOURS
                        )
                    ).build()
                    Tasks.await(client.fetchPlace(req)).place
                }.getOrNull() ?: return@async null

                val title = fetched.name ?: return@async null
                val meta: PhotoMetadata? = fetched.photoMetadatas?.firstOrNull()

                val d = fetched.latLng?.let { ll ->
                    distanceMeters(
                        center.latitude,
                        center.longitude,
                        ll.latitude,
                        ll.longitude
                    ).roundToInt()
                }
                val rating = fetched.rating?.toFloat()
                val isOpen = computeIsOpenNow(fetched.openingHours)

                UiPlace(
                    id = placeId,
                    title = title,
                    photo = null,           // no photo yet
                    distanceMeters = d,
                    rating = rating,
                    isOpenNow = isOpen,
                    photoMetadata = meta
                )
            ).build()
            Tasks.await(client.fetchPlace(req)).place
        }.getOrNull() ?: return@mapNotNull null

        val title = fetched.name ?: return@mapNotNull null
        val meta: PhotoMetadata? = fetched.photoMetadatas?.firstOrNull()
        val bmp: Bitmap? = if (meta != null) {
            runCatching {
                val preq = FetchPhotoRequest
                    .builder(meta)
                    .setMaxWidth(1280)
                    .setMaxHeight(720)
                    .build()
                Tasks.await(client.fetchPhoto(preq)).bitmap
            }.getOrNull()
        } else null

        val d = fetched.latLng?.let { ll ->
            distanceMeters(
                center.latitude,
                center.longitude,
                ll.latitude,
                ll.longitude
            ).roundToInt()
        }

        UiPlace(
            id = placeId,
            title = title,
            photo = bmp,
            distanceMeters = d,
            rating = rating,
            isOpenNow = isOpen
        )
    }
}

/* --------------------------- Lazy photo loader ---------------------- */

@Composable
private fun PlaceCardWithLazyPhoto(
    place: UiPlace,
    photo: Bitmap?,
    client: PlacesClient,
    isVisible: Boolean,
    onPhotoLoaded: (Bitmap?) -> Unit,
    isFavorited: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var localPhoto by remember(place.id) { mutableStateOf(photo) }

    // Only start fetching when the row is actually visible
    LaunchedEffect(place.id, isVisible) {
        if (!isVisible) return@LaunchedEffect
        if (localPhoto == null && place.photoMetadata != null) {
            // Small delay to avoid flapping when scrolling quickly
            delay(150)
            if (!isVisible) return@LaunchedEffect

            val bmp = fetchPhotoBitmap(client, place.photoMetadata)
            localPhoto = bmp
            onPhotoLoaded(bmp)
        }
    }

    PlaceCardMinimal(
        place = place,
        photo = localPhoto,
        isFavorited = isFavorited,
        onClick = onClick,
        onToggleFavorite = onToggleFavorite
    )
}

private suspend fun fetchPhotoBitmap(
    client: PlacesClient,
    meta: PhotoMetadata
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val preq = FetchPhotoRequest.builder(meta)
            .setMaxWidth(PHOTO_MAX_WIDTH)
            .setMaxHeight(PHOTO_MAX_HEIGHT)
            .build()
        Tasks.await(client.fetchPhoto(preq)).bitmap
    }.getOrNull()
}

/* --------------------------- Apply filters to section -------------- */

private fun applyFiltersToSection(
    section: UiSection,
    favIds: Set<String>,
    f: DiscoverFilters
): UiSection {
    var items = section.items

    if (f.openNow) items = items.filter { it.isOpenNow == true }
    f.minRating?.let { min -> items = items.filter { (it.rating ?: 0f) >= min } }
    if (f.onlyWithPhoto) items = items.filter { it.photoMetadata != null }
    if (f.onlyFavorites) items = items.filter { favIds.contains(it.id) }

    items = when (f.sort) {
        SortOption.NEAREST -> items.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
        SortOption.FARTHEST -> items.sortedByDescending { it.distanceMeters ?: Int.MIN_VALUE }
    }
    return section.copy(items = items)
}

/* --------------------------- Card UI --------------------------- */

@Composable
fun PlaceCardMinimal(
    place: UiPlace,
    photo: Bitmap? = place.photo, // use passed photo (lazy-loaded), fallback to place.photo
    isFavorited: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        label = "fav-press"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFFDE7) // very light yellow
        )
    ) {
        Column {
            if (photo != null) {
                Image(
                    bitmap = photo.asImageBitmap(),
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
                        .background(Color(0xFFFFECB3)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFE082))
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        place.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    place.distanceMeters?.let {
                        Text(
                            formatDistanceImperial(it),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                IconButton(
                    onClick = {
                        pressed = true
                        onToggleFavorite()
                    },
                    modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
                ) {
                    if (isFavorited)
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = "Added",
                            tint = Color.Red
                        )
                    else
                        Icon(
                            Icons.Outlined.FavoriteBorder,
                            contentDescription = "Save"
                        )
                }
            }
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            pressed = false
        }
    }
}

/* --------------------------- Titles & Utils --------------------------- */

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
    return special[type] ?: type
        .split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { c -> c.titlecase() }
        }
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

private fun distanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
        sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/**
 * Match the recommendation carousel style:
 * - If >= 1 mile (1609m): show miles with 1 decimal (e.g., "0.9 mi")
 * - Else: show feet as integer (e.g., "800 ft")
 */
private fun formatDistanceImperial(meters: Int): String {
    val m = meters.toDouble()
    return if (m >= 1609.0) {
        val milesTimes10 = ((m / 1609.34) * 10.0)
        val roundedTimes10 = kotlin.math.round(milesTimes10)
        val miles = roundedTimes10 / 10.0
        "$miles mi"
    } else {
        val feet = kotlin.math.round(m * 3.28084).toInt()
        "$feet ft"
    }
}

/* --------------------------- Open-now computation --------------------------- */

private fun computeIsOpenNow(hours: OpeningHours?): Boolean? {
    val periods: List<Period> = hours?.periods ?: return null

    val cal = Calendar.getInstance()
    val currentIdx = calendarDayToIdx(cal.get(Calendar.DAY_OF_WEEK)) // 0..6
    val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    for (p in periods) {
        val open = p.open ?: continue
        val close = p.close ?: continue

        val oDayIdx = open.day?.let { dayOfWeekToIdx(it) } ?: continue
        val cDayIdx = close.day?.let { dayOfWeekToIdx(it) } ?: oDayIdx

        val oMin = localTimeToMinutes(open.time) ?: continue
        val cMin = localTimeToMinutes(close.time) ?: continue

        if (oDayIdx == cDayIdx) {
            if (currentIdx == oDayIdx && nowMinutes in oMin until cMin) return true
        } else if ((oDayIdx + 1) % 7 == cDayIdx) {
            if (currentIdx == oDayIdx && nowMinutes >= oMin) return true
            if (currentIdx == cDayIdx && nowMinutes < cMin) return true
        } else {
            if (currentIdx == oDayIdx && nowMinutes >= oMin) return true
            if (currentIdx == cDayIdx && nowMinutes < cMin) return true
        }
    }
    return false
}

private fun localTimeToMinutes(t: LocalTime?): Int? =
    t?.let { it.hours * 60 + it.minutes }

private fun dayOfWeekToIdx(d: DayOfWeek): Int = when (d) {
    DayOfWeek.MONDAY -> 0
    DayOfWeek.TUESDAY -> 1
    DayOfWeek.WEDNESDAY -> 2
    DayOfWeek.THURSDAY -> 3
    DayOfWeek.FRIDAY -> 4
    DayOfWeek.SATURDAY -> 5
    DayOfWeek.SUNDAY -> 6
    else -> 0
}

private fun calendarDayToIdx(dayOfWeek: Int): Int = when (dayOfWeek) {
    Calendar.MONDAY -> 0
    Calendar.TUESDAY -> 1
    Calendar.WEDNESDAY -> 2
    Calendar.THURSDAY -> 3
    Calendar.FRIDAY -> 4
    Calendar.SATURDAY -> 5
    Calendar.SUNDAY -> 6
    else -> 0
}
