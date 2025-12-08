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
import com.google.android.libraries.places.api.model.*
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.unh.hoppin_android_app.viewmodels.RecommendationViewModel
import com.unh.hoppin_android_app.viewmodels.RecommendationsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/* ------------------------------------------------------------------ */
/* Discover cache                                                     */
/* ------------------------------------------------------------------ */

private const val DISCOVER_CACHE_MAX_AGE_MS = 10 * 60 * 1000L // 10 minutes

private data class DiscoverCacheKey(
    val roundedLat: Double,
    val roundedLng: Double,
    val typesKey: String,
    val categoryId: Int?,
    val radiusMeters: Int,
    val maxResults: Int
)

private data class DiscoverCacheEntry(
    val timestamp: Long,
    val sections: List<UiSection>
)

private object DiscoverMemoryCache {
    private val cache = mutableMapOf<DiscoverCacheKey, DiscoverCacheEntry>()

    private fun makeKey(
        center: LatLng,
        types: List<String>,
        categoryId: Int?,
        radiusMeters: Int,
        maxResults: Int
    ): DiscoverCacheKey {
        val roundedLat = String.format("%.3f", center.latitude).toDouble()
        val roundedLng = String.format("%.3f", center.longitude).toDouble()
        val typesKey = types.map { it.trim() }.sorted().joinToString(",")

        return DiscoverCacheKey(
            roundedLat = roundedLat,
            roundedLng = roundedLng,
            typesKey = typesKey,
            categoryId = categoryId,
            radiusMeters = radiusMeters,
            maxResults = maxResults
        )
    }

    fun get(
        center: LatLng,
        types: List<String>,
        categoryId: Int?,
        radiusMeters: Int,
        maxResults: Int
    ): List<UiSection>? {
        val key = makeKey(center, types, categoryId, radiusMeters, maxResults)
        val now = System.currentTimeMillis()
        val entry = cache[key] ?: return null
        return if (now - entry.timestamp <= DISCOVER_CACHE_MAX_AGE_MS) {
            entry.sections
        } else {
            cache.remove(key)
            null
        }
    }

    fun put(
        center: LatLng,
        types: List<String>,
        categoryId: Int?,
        radiusMeters: Int,
        maxResults: Int,
        sections: List<UiSection>
    ) {
        val key = makeKey(center, types, categoryId, radiusMeters, maxResults)
        cache[key] = DiscoverCacheEntry(
            timestamp = System.currentTimeMillis(),
            sections = sections
        )
    }
}

/* ------------------------------------------------------------------ */
/* Filters & Options                                                  */
/* ------------------------------------------------------------------ */

enum class SortOption { NEAREST, FARTHEST }

data class DiscoverFilters(
    val sort: SortOption = SortOption.NEAREST,
    val openNow: Boolean = false,
    val minRating: Float? = null,
    val onlyWithPhoto: Boolean = false,
    val onlyFavorites: Boolean = false
)

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
    recoVm: RecommendationViewModel,        // shared VM from MainActivity
    radiusMeters: Int = 1_600,              // ~1 mile
    maxResults: Int = 20,
    onBack: () -> Unit = {},
    onPlaceClick: (UiPlace) -> Unit = {},
    onOpenFavorites: () -> Unit = {}
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val favIds: Set<String> by FavoritesRepositoryFirebase.favoriteIdsFlow()
        .collectAsState(initial = emptySet())
    var transientAdded by remember { mutableStateOf(setOf<String>()) }

    var filters by remember {
        mutableStateOf(
            DiscoverFilters(
                sort = SortOption.NEAREST,
                openNow = false,
                minRating = null,
                onlyWithPhoto = false,
                onlyFavorites = false
            )
        )
    }

    val dynamicTitle = remember(selectedTypes, selectedCategoryId) {
        when {
            selectedTypes.size == 1 -> readableFromType(selectedTypes.first())
            selectedTypes.size > 1 -> "Discover"
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

    // ⭐ page size and current batch size
    val pageSize = 5
    var currentMaxResults by remember { mutableStateOf(pageSize) }

    // Reset batch size when category / types / center change
    LaunchedEffect(activeTypes, selectedCategoryId, center) {
        currentMaxResults = pageSize
    }

    // Bootstrap from cache (optional, using first batch size)
    val initialUi: ListUi = remember(center, activeTypes, selectedCategoryId, radiusMeters, maxResults) {
        val c = center
        if (c != null) {
            val cached = DiscoverMemoryCache.get(
                center = c,
                types = activeTypes,
                categoryId = selectedCategoryId,
                radiusMeters = radiusMeters,
                maxResults = pageSize.coerceAtMost(maxResults)
            )
            if (cached != null) {
                ListUi(loading = false, sections = cached, error = null)
            } else {
                ListUi(loading = true, sections = emptyList(), error = null)
            }
        } else {
            ListUi(loading = true, sections = emptyList(), error = null)
        }
    }

    var ui by remember { mutableStateOf(initialUi) }

    val recoUi by recoVm.ui.collectAsState()

    /* --------------------------- Loader with batching --------------------------- */

    LaunchedEffect(
        activeTypes,
        center,
        radiusMeters,
        currentMaxResults,       // ⭐ re-run when we ask for more
        placesClient,
        context,
        recoUi.flatItems
    ) {
        val currentCenter = center
        if (currentCenter == null) {
            ui = ListUi(loading = true, sections = emptyList(), error = null)
            return@LaunchedEffect
        }

        val effectiveMax = currentMaxResults.coerceAtMost(maxResults)

        // 1) Try sections from Recommendations only for the first batch
        val derivedSections = sectionsFromRecommendationsForCategory(
            recoUi = recoUi,
            selectedCategoryId = selectedCategoryId
        )

        if (!derivedSections.isNullOrEmpty() && effectiveMax == pageSize) {
            ui = ListUi(
                loading = false,
                sections = derivedSections,
                error = null
            )
            DiscoverMemoryCache.put(
                center = currentCenter,
                types = activeTypes,
                categoryId = selectedCategoryId,
                radiusMeters = radiusMeters,
                maxResults = effectiveMax,
                sections = derivedSections
            )
            return@LaunchedEffect
        }

        // 2) Try Discover cache for current batch size
        val cached = DiscoverMemoryCache.get(
            center = currentCenter,
            types = activeTypes,
            categoryId = selectedCategoryId,
            radiusMeters = radiusMeters,
            maxResults = effectiveMax
        )
        if (cached != null) {
            ui = ListUi(loading = false, sections = cached, error = null)
            return@LaunchedEffect
        }

        // 3) Fallback: call Places for THIS batch size
        ui = ui.copy(loading = ui.sections.isEmpty(), error = null)
        val client = placesClient ?: Places.createClient(context)
        val result = runCatching {
            loadNearbySectionsWithPhotosDistanceRatingOpenNow(
                client = client,
                center = currentCenter,
                typesOrdered = activeTypes,
                radiusMeters = radiusMeters,
                maxResults = effectiveMax
            )
        }

        ui = result.fold(
            onSuccess = { freshSections ->
                val merged = mergeSectionsById(old = ui.sections, fresh = freshSections)
                DiscoverMemoryCache.put(
                    center = currentCenter,
                    types = activeTypes,
                    categoryId = selectedCategoryId,
                    radiusMeters = radiusMeters,
                    maxResults = effectiveMax,
                    sections = merged
                )
                ListUi(loading = false, sections = merged, error = null)
            },
            onFailure = {
                if (ui.sections.isNotEmpty()) {
                    ui.copy(loading = false, error = it.message ?: "Failed to load places")
                } else {
                    ListUi(
                        loading = false,
                        sections = emptyList(),
                        error = it.message ?: "Failed to load places"
                    )
                }
            }
        )
    }

    /* --------------------------- UI --------------------------- */

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dynamicTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xfff8f0e3),
                    titleContentColor = Color(0xFF000000)
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Transparent
    ) { inner ->
        when {
            center == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) {
                Text("Detecting your location…")
            }

            ui.loading && ui.sections.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            ui.error != null && ui.sections.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner),
                contentAlignment = Alignment.Center
            ) { Text(ui.error!!, color = MaterialTheme.colorScheme.error) }

            else -> {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(horizontal = 16.dp)
                ) {
                    FilterBar(
                        filters = filters,
                        onChange = { filters = it }
                    )

                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (ui.sections.isEmpty()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No nearby places")
                                }
                            }
                        } else {
                            ui.sections.forEach { rawSection ->
                                val section = applyFiltersToSection(rawSection, favIds, filters)

                                if (section.items.isNotEmpty()) {
                                    // ✅ show ALL fetched items for this batch,
                                    // older ones stay, new ones appended at bottom
                                    items(section.items, key = { it.id }) { place ->
                                        val isFav =
                                            favIds.contains(place.id) || transientAdded.contains(place.id)

                                        PlaceCardMinimal(
                                            place = place,
                                            isFavorited = isFav,
                                            onClick = { onPlaceClick(place) },
                                            onToggleFavorite = {
                                                transientAdded = transientAdded + place.id
                                                scope.launch {
                                                    runCatching {
                                                        FavoritesRepositoryFirebase.add(placeId = place.id)
                                                    }
                                                    snackbarHostState.currentSnackbarData?.dismiss()
                                                    snackbarHostState.showSnackbar("Added to favourites")
                                                    kotlinx.coroutines.delay(1000)
                                                    transientAdded = transientAdded - place.id
                                                }
                                            }
                                        )
                                    }

                                    // ✅ Show Load More as long as we can request more from API
                                    val canLoadMoreForSection =
                                        section.items.size >= currentMaxResults &&
                                                currentMaxResults < maxResults

                                    if (canLoadMoreForSection) {
                                        item(key = "loadmore-${section.type}") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        currentMaxResults =
                                                            (currentMaxResults + pageSize)
                                                                .coerceAtMost(maxResults)
                                                    }
                                                ) {
                                                    Text("Load more")
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
        }
    }
}

/* --------------------------- Merge helper --------------------------- */

/**
 * Ensures that previously fetched places stay in the same order,
 * and new places (by id) get APPENDED below the old ones.
 */
private fun mergeSectionsById(
    old: List<UiSection>,
    fresh: List<UiSection>
): List<UiSection> {
    if (old.isEmpty()) return fresh

    val oldByType = old.associateBy { it.type }
    val freshByType = fresh.associateBy { it.type }

    val allTypes = (oldByType.keys + freshByType.keys).toSet()

    return allTypes.mapNotNull { type ->
        val oldSection = oldByType[type]
        val freshSection = freshByType[type]

        when {
            oldSection == null && freshSection != null -> freshSection
            freshSection == null && oldSection != null -> oldSection
            oldSection != null && freshSection != null -> {
                val existingIds = oldSection.items.map { it.id }.toHashSet()
                val appended = freshSection.items.filter { it.id !in existingIds }
                oldSection.copy(items = oldSection.items + appended)
            }
            else -> null
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
        color = Color.Transparent
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val selectedColor = Color(0xFF023c85)
            val unselectedColor = Color(0xff45c2db)

            @Composable
            fun chipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
                selectedContainerColor = if (selected) selectedColor else unselectedColor,
                selectedLabelColor = Color.White,
                containerColor = if (!selected) unselectedColor else selectedColor,
                labelColor = Color.White
            )

            item {
                FilterChip(
                    selected = filters.sort == SortOption.NEAREST,
                    onClick = {
                        onChange(
                            filters.copy(
                                sort = if (filters.sort == SortOption.NEAREST)
                                    SortOption.FARTHEST else SortOption.NEAREST
                            )
                        )
                    },
                    label = {
                        Text(if (filters.sort == SortOption.NEAREST) "Nearest" else "Farthest")
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
                val isMin4Selected = (filters.minRating ?: 0f) >= 4.0f
                FilterChip(
                    selected = isMin4Selected,
                    onClick = {
                        onChange(
                            filters.copy(
                                minRating = if (isMin4Selected) null else 4.0f
                            )
                        )
                    },
                    label = { Text("4.0+") },
                    colors = chipColors(isMin4Selected)
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
    val photo: Bitmap?,
    val distanceMeters: Int? = null,
    val rating: Float? = null,
    val isOpenNow: Boolean? = null
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

/* --------------------------- From Recommendations --------------------------- */

private fun sectionsFromRecommendationsForCategory(
    recoUi: RecommendationsUiState,
    selectedCategoryId: Int?
): List<UiSection>? {
    if (selectedCategoryId == null) return null

    val catTitle = categoryTitle(selectedCategoryId)
    val itemsForCategory = recoUi.flatItems.filter { it.categoryTitle == catTitle }
    if (itemsForCategory.isEmpty()) return null

    val uiItems = itemsForCategory.map { rec ->
        UiPlace(
            id = rec.placeId,
            title = rec.title,
            photo = rec.bitmap,
            distanceMeters = rec.distanceMeters.roundToInt(),
            rating = null,
            isOpenNow = null
        )
    }

    return listOf(UiSection(type = "places", items = uiItems))
}

/* --------------------------- Places loading --------------------------- */

private suspend fun loadNearbySectionsWithPhotosDistanceRatingOpenNow(
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
        UiSection(
            type = t,
            items = searchNearbyOneTypeHydrated(client, center, t, radiusMeters, maxResults)
        )
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

    val nearbyFields = listOf(
        Place.Field.ID,
        Place.Field.NAME,
        Place.Field.LAT_LNG,
        Place.Field.PHOTO_METADATAS,
        Place.Field.RATING,
        Place.Field.OPENING_HOURS
    )

    val nearbyReq = SearchNearbyRequest
        .builder(bounds, nearbyFields)
        .setMaxResultCount(maxResults)
        .apply {
            if (!type.isNullOrBlank()) setIncludedTypes(listOf(type))
        }
        .build()

    val nearbyResp = runCatching { Tasks.await(client.searchNearby(nearbyReq)) }.getOrNull()
        ?: return emptyList()
    val candidates = nearbyResp.places.orEmpty()

    return candidates.mapNotNull { p ->
        val placeId = p.id ?: return@mapNotNull null
        val title = p.name ?: return@mapNotNull null

        val meta: PhotoMetadata? = p.photoMetadatas?.firstOrNull()
        val bmp: Bitmap? = if (meta != null) {
            runCatching {
                val preq =
                    FetchPhotoRequest.builder(meta).setMaxWidth(1280).setMaxHeight(720).build()
                Tasks.await(client.fetchPhoto(preq)).bitmap
            }.getOrNull()
        } else null

        val d = p.latLng?.let { ll ->
            distanceMeters(center.latitude, center.longitude, ll.latitude, ll.longitude)
                .roundToInt()
        }
        val rating = p.rating?.toFloat()
        val isOpen = computeIsOpenNow(p.openingHours)

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

/* --------------------------- Apply filters --------------------------- */

private fun applyFiltersToSection(
    section: UiSection,
    favIds: Set<String>,
    f: DiscoverFilters
): UiSection {
    var items = section.items

    if (f.openNow && items.any { it.isOpenNow != null }) {
        items = items.filter { it.isOpenNow == true }
    }

    f.minRating?.let { min ->
        if (items.any { it.rating != null }) {
            items = items.filter { (it.rating ?: 0f) >= min }
        }
    }

    if (f.onlyWithPhoto) items = items.filter { it.photo != null }
    if (f.onlyFavorites) items = items.filter { favIds.contains(it.id) }

    items = when (f.sort) {
        SortOption.NEAREST -> items.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }
        SortOption.FARTHEST -> items.sortedByDescending { it.distanceMeters ?: Int.MIN_VALUE }
    }
    return section.copy(items = items)
}

/* --------------------------- Card UI (WITH heart) --------------------------- */

@Composable
fun PlaceCardMinimal(
    place: UiPlace,
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
        colors = CardDefaults.cardColors(Color(0xfff8f0e3))
    ) {
        Column {
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
                        Icon(Icons.Filled.Favorite, contentDescription = "Added", tint = Color.Red)
                    else
                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Save")
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
    return special[type]
        ?: type.split('_')
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

private fun distanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) *
            cos(Math.toRadians(lat2)) *
            sin(dLon / 2) *
            sin(dLon / 2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun formatDistanceImperial(m: Int): String {
    val meters = m.toDouble()
    return if (meters >= 1609.0) {
        val miles = ((meters / 1609.34) * 10.0).roundToInt() / 10.0
        "$miles mi away"
    } else {
        val feet = (meters * 3.28084).roundToInt()
        "$feet ft away"
    }
}

/* --------------------------- Open-now computation --------------------------- */

private fun computeIsOpenNow(hours: OpeningHours?): Boolean? {
    val periods: List<Period> = hours?.periods ?: return null

    val cal = Calendar.getInstance()
    val currentIdx = calendarDayToIdx(cal.get(Calendar.DAY_OF_WEEK))
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
