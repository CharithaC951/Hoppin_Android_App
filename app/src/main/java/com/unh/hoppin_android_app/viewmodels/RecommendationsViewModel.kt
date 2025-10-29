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
import com.unh.hoppin_android_app.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.collections.map
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class RecommendationItem(
    val title: String,
    val bitmap: Bitmap?,
    val distanceMeters: Double
)

data class RecommendationItemWithCategory(
    val categoryTitle: String,
    val title: String,
    val bitmap: Bitmap?,
    val distanceMeters: Double
)

data class RecommendationSection(
    val category: Category,
    val items: List<RecommendationItem>
)

data class RecommendationsUiState(
    val loading: Boolean = false,
    val sections: List<RecommendationSection> = emptyList(),
    val flatItems: List<RecommendationItemWithCategory> = emptyList(),
    val error: String? = null
)

class RecommendationViewModel : ViewModel() {

    private val _ui = MutableStateFlow(RecommendationsUiState())
    val ui: StateFlow<RecommendationsUiState> = _ui.asStateFlow()

    private var lastAll: List<Place> = emptyList()

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
        val max = maxResults.coerceIn(1, 20)

        _ui.value = _ui.value.copy(loading = true, error = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                ensurePlacesInitialized(context, apiKey)
                val client: PlacesClient = Places.createClient(context)

                // Fields to retrieve
                val fields: List<Place.Field> = listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG,
                    Place.Field.TYPES,
                    Place.Field.PHOTO_METADATAS
                )

                // Search bounds
                val restriction: LocationRestriction =
                    CircularBounds.newInstance(center, radiusMeters)

                // Union of Place.Type across your UI categories
                val unionTypes: List<String> = categories
                    .mapNotNull { categoryToTypes[it.title] }
                    .flatten()
                    .distinct()

                val req = SearchNearbyRequest.builder(restriction, fields)
                    .setIncludedTypes(unionTypes)
                    .setRankPreference(SearchNearbyRequest.RankPreference.POPULARITY)
                    .setMaxResultCount(max)
                    .build()


                val resp = client.searchNearby(req).await()
                val allPlaces: List<Place> = resp.places ?: emptyList()
                lastAll = allPlaces

                // Build sections (use loops so we can call suspend photo fetch safely)
                val sections = mutableListOf<RecommendationSection>()
                for (cat in categories) {
                    val items = mutableListOf<RecommendationItem>()
                    var taken = 0
                    for (place in allPlaces) {
                        if (!matches(cat, place)) continue
                        val item = buildItem(client, place, center, fetchThumbnails)
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

                _ui.value = RecommendationsUiState(
                    loading = false,
                    sections = sections,
                    flatItems = flatItems,
                    error = null
                )

            } catch (t: Throwable) {
                Log.e("RecommendationVM", "load() failed", t)
                _ui.value = RecommendationsUiState(
                    loading = false,
                    sections = emptyList(),
                    error = t.message ?: "Failed to load"
                )
            }
        }
    }

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

    private fun ensurePlacesInitialized(context: Context, apiKey: String?) {
        if (!Places.isInitialized()) {
            require(!apiKey.isNullOrBlank()) {
                "Places SDK not initialized. Pass apiKey or init in Application.onCreate()."
            }
            Places.initialize(context.applicationContext, apiKey)
        }
    }

    private fun matches(cat: Category, p: Place): Boolean {
        val needed = categoryToTypes[cat.title] ?: return false
        val types = p.types ?: return false
        return types.any { it.name.lowercase() in needed }
    }
    private suspend fun buildItem(
        client: PlacesClient,
        place: Place,
        center: LatLng,
        fetchThumb: Boolean
    ): RecommendationItem? {
        val title: String = place.name ?: return null
        val ll = place.latLng ?: return null
        val distance = haversine(center.latitude, center.longitude, ll.latitude, ll.longitude)

        val bmp: Bitmap? = if (fetchThumb) {
            val meta: PhotoMetadata? = place.photoMetadatas?.firstOrNull()
            if (meta != null) {
                runCatching {
                    client.fetchPhoto(
                        FetchPhotoRequest.builder(meta)
                            .setMaxWidth(640).setMaxHeight(360) // small thumbnail for cards
                            .build()
                    ).await().bitmap
                }.getOrNull()
            } else null
        } else null

        return RecommendationItem(title = title, bitmap = bmp, distanceMeters = distance)
    }

    private fun offlineItem(place: Place, center: LatLng): RecommendationItem? {
        val title: String = place.name ?: return null
        val ll = place.latLng ?: return null
        val distance = haversine(center.latitude, center.longitude, ll.latitude, ll.longitude)
        return RecommendationItem(title = title, bitmap = null, distanceMeters = distance)
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private val categoryToTypes: Map<String, List<String>> = mapOf(
        // 1. Explore – outdoor attractions and points of interest
        "Explore" to listOf(
            "tourist_attraction",
            "museum",
            "art_gallery",
            "park",
        ),

        // 2. Refresh – food, drinks, hangouts
        "Refresh" to listOf(
            "restaurant",
            "bar",
            "cafe",
            "bakery"
        ),

        // 3. Entertain – leisure and social activities
        "Entertain" to listOf(
            "movie_theater",
            "night_club",
            "bowling_alley",
            "casino"
        ),

        // 4. ShopStop – shopping and retail
        "ShopStop" to listOf(
            "shopping_mall",
            "clothing_store",
            "department_store",
            "store",
            "supermarket"
        ),

        // 5. Relax – accommodation and rest
        "Relax" to listOf(
            "spa",
            "lodging",
            "campground"
        ),

        // 6. Wellbeing – health and personal care
        "Wellbeing" to listOf(
            "gym",
            "pharmacy",
            "doctor",
            "beauty_salon"
        ),

        // 7. Emergency – urgent or public services
        "Emergency" to listOf(
            "hospital",
            "police",
            "fire_station"
        ),

        // 8. Services – everyday support services
        "Services" to listOf(
            "post_office",
            "bank",
            "atm",
            "gas_station",
            "car_repair"
        )
    )
}
