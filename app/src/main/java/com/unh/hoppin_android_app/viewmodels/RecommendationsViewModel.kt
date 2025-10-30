package com.unh.hoppin_android_app.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.LocationRestriction
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.unh.hoppin_android_app.CategoriesRepository
import com.unh.hoppin_android_app.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Represents a single recommended place item. */
data class RecommendationItem(
    val title: String,
    val bitmap: Bitmap?,
    val distanceMeters: Double
)

/** Same as RecommendationItem, but also includes the category name it belongs to. */
data class RecommendationItemWithCategory(
    val categoryTitle: String,
    val title: String,
    val bitmap: Bitmap?,
    val distanceMeters: Double
)

/** Groups several RecommendationItems under a single Category. */
data class RecommendationSection(
    val category: Category,
    val items: List<RecommendationItem>
)

/** Represents the entire UI state for recommendations. */
data class RecommendationsUiState(
    val loading: Boolean = false,
    val sections: List<RecommendationSection> = emptyList(),
    val flatItems: List<RecommendationItemWithCategory> = emptyList(),
    val error: String? = null
)

class RecommendationViewModel : ViewModel() {

    // MutableStateFlow for UI state (used by Compose to observe updates)
    private val _ui = MutableStateFlow(RecommendationsUiState())
    val ui: StateFlow<RecommendationsUiState> = _ui.asStateFlow()

    // Stores the most recent list of all fetched places
    private var lastAll: List<Place> = emptyList()

    /**
     * Fetches nearby recommendations based on user location and categories.
     *
     * @param context Android context (required for Places SDK)
     * @param center User's current location (LatLng)
     * @param categories List of categories to search (e.g. restaurants, cafes)
     * @param radiusMeters Search radius in meters
     * @param maxResults Maximum total places to retrieve (limited by API)
     * @param perCategory Limit of places shown per category
     * @param apiKey Optional API key for initializing Places SDK
     * @param fetchThumbnails Whether to fetch image thumbnails for places
     */
    fun load(
        context: Context,
        center: LatLng,
        categories: List<Category>,
        radiusMeters: Double = 1500.0,
        maxResults: Int = 60,
        perCategory: Int = 4,
        apiKey: String? = null,
        fetchThumbnails: Boolean = true
    ) {
        // Cap max results between 1–20 as per Places API limitations
        val max = maxResults.coerceIn(1, 20)

        // Mark UI as loading
        _ui.value = _ui.value.copy(loading = true, error = null)

        // Launch the data-fetching task in background (IO thread)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Initialize the Places SDK if needed
                ensurePlacesInitialized(context, apiKey)
                val client: PlacesClient = Places.createClient(context)

                // Fields to be retrieved for each place
                val fields: List<Place.Field> = listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG,
                    Place.Field.TYPES,
                    Place.Field.PHOTO_METADATAS
                )

                // Define the circular search boundary
                val restriction: LocationRestriction = CircularBounds.newInstance(center, radiusMeters)

                // Merge all desired Place types across categories
                val unionTypes: List<String> = CategoriesRepository.unionTypesFor(categories)

                // Build the search request
                val req = SearchNearbyRequest.builder(restriction, fields)
                    .setIncludedTypes(unionTypes)
                    .setRankPreference(SearchNearbyRequest.RankPreference.POPULARITY)
                    .setMaxResultCount(max)
                    .build()

                // Execute API call
                val resp = client.searchNearby(req).await()
                val allPlaces: List<Place> = resp.places ?: emptyList()
                lastAll = allPlaces

                val sections = mutableListOf<RecommendationSection>()
                for (cat in categories) {
                    val items = mutableListOf<RecommendationItem>()
                    var taken = 0

                    // Loop through all places and check if they match this category
                    for (place in allPlaces) {
                        if (!matches(cat, place)) continue
                        val item = buildItem(client, place, center, fetchThumbnails)
                        if (item != null) {
                            items += item
                            taken++
                            if (taken >= perCategory) break // Limit per category
                        }
                    }

                    if (items.isNotEmpty()) sections += RecommendationSection(cat, items)
                }

                // Flatten all sections for easier list display
                val flatItems = sections.flatMap { sec ->
                    sec.items.map { it ->
                        RecommendationItemWithCategory(
                            categoryTitle = sec.category.title,
                            title = it.title,
                            bitmap = it.bitmap,
                            distanceMeters = it.distanceMeters
                        )
                    }
                }

                // Update UI state (success)
                _ui.value = RecommendationsUiState(
                    loading = false,
                    sections = sections,
                    flatItems = flatItems,
                    error = null
                )

            } catch (t: Throwable) {
                // Handle exceptions gracefully
                Log.e("RecommendationVM", "load() failed", t)
                _ui.value = RecommendationsUiState(
                    loading = false,
                    sections = emptyList(),
                    error = t.message ?: "Failed to load"
                )
            }
        }
    }

    /**
     * Builds recommendation sections from previously fetched data (`lastAll`),
     * avoiding new network calls. Used for local filtering or sorting.
     */
    fun deriveLocally(
        center: LatLng,
        categories: List<Category>,
        perCategory: Int = 4
    ) {
        if (lastAll.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val sections = mutableListOf<RecommendationSection>()

            for (cat in categories) {
                val items = mutableListOf<RecommendationItem>()
                var taken = 0
                for (place in lastAll) {
                    if (!matches(cat, place)) continue
                    val item = offlineItem(place, center)
                    if (item != null) {
                        items += item
                        taken++
                        if (taken >= perCategory) break
                    }
                }
                if (items.isNotEmpty()) sections += RecommendationSection(cat, items)
            }
            val flatItems = sections.flatMap { sec ->
                sec.items.map { it ->
                    RecommendationItemWithCategory(
                        categoryTitle = sec.category.title,
                        title = it.title,
                        bitmap = it.bitmap,
                        distanceMeters = it.distanceMeters
                    )
                }
            }

            _ui.value = _ui.value.copy(sections = sections, flatItems = flatItems)
        }
    }

    /**
     * Ensures the Places SDK is initialized before making any API calls.
     */
    private fun ensurePlacesInitialized(context: Context, apiKey: String?) {
        if (!Places.isInitialized()) {
            require(!apiKey.isNullOrBlank()) {
                "Places SDK not initialized. Pass apiKey or init in Application.onCreate()."
            }
            Places.initialize(context.applicationContext, apiKey)
        }
    }

    /** Returns true if a given place belongs to the given category. */
    private fun matches(cat: Category, p: Place): Boolean =
        CategoriesRepository.placeMatchesCategory(cat.id, p.types)

    /**
     * Converts a Place object into a RecommendationItem.
     * Optionally fetches a thumbnail image if available.
     */
    private suspend fun buildItem(
        client: PlacesClient,
        place: Place,
        center: LatLng,
        fetchThumb: Boolean
    ): RecommendationItem? {
        val title: String = place.name ?: return null
        val ll = place.latLng ?: return null

        // Calculate distance between current location and place
        val distance = haversine(center.latitude, center.longitude, ll.latitude, ll.longitude)

        // Attempt to fetch thumbnail image from Places API
        val bmp: Bitmap? = if (fetchThumb) {
            val meta: PhotoMetadata? = place.photoMetadatas?.firstOrNull()
            if (meta != null) {
                runCatching {
                    client.fetchPhoto(
                        FetchPhotoRequest.builder(meta)
                            .setMaxWidth(640)
                            .setMaxHeight(360) // Reasonable thumbnail size
                            .build()
                    ).await().bitmap
                }.getOrNull()
            } else null
        } else null

        return RecommendationItem(title = title, bitmap = bmp, distanceMeters = distance)
    }

    /**
     * Creates an offline recommendation item (no network call or image fetching).
     */
    private fun offlineItem(place: Place, center: LatLng): RecommendationItem? {
        val title: String = place.name ?: return null
        val ll = place.latLng ?: return null
        val distance = haversine(center.latitude, center.longitude, ll.latitude, ll.longitude)
        return RecommendationItem(title = title, bitmap = null, distanceMeters = distance)
    }

    /**
     * Calculates distance between two coordinates using the Haversine formula.
     * @return Distance in meters.
     */
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
