package com.unh.hoppin_android_app.viewmodels

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.LocationRestriction
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.unh.hoppin_android_app.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

// ---------- Inline models ----------
data class RecommendationItem(
    val title: String,
    val bitmap: Bitmap?,
    val distanceMeters: Double
)

data class RecommendationSection(
    val category: Category,
    val items: List<RecommendationItem>
)

/** Flat item that also carries the category label for UI (optional to show) */
data class RecommendationItemWithCategory(
    val categoryTitle: String,
    val title: String,
    val bitmap: Bitmap?,
    val distanceMeters: Double
)

// ---------- UI state ----------
data class RecommendationsUiState(
    val loading: Boolean = false,
    val sections: List<RecommendationSection> = emptyList(),
    val flatItems: List<RecommendationItemWithCategory> = emptyList(),
    val error: String? = null
)

class RecommendationViewModel : ViewModel() {

    private val _ui = MutableStateFlow(RecommendationsUiState())
    val ui = _ui.asStateFlow()

    // Map your UI categories → Places "includedTypes"
    private val categoryToTypes: Map<Int, List<String>> = mapOf(
        1 to listOf("tourist_attraction", "point_of_interest", "park", "museum"),     // Explore
        2 to listOf("restaurant", "cafe", "bakery"),                                   // Refresh
        3 to listOf("movie_theater", "bowling_alley", "amusement_park", "night_club"), // Entertain
        4 to listOf("shopping_mall", "clothing_store", "department_store", "convenience_store"), // ShopStop
        5 to listOf("spa", "park"),                                                    // Relax
        6 to listOf("gym", "yoga_studio", "pharmacy")
    )

    fun load(
        context: Context,
        apiKey: String,
        center: LatLng,
        categories: List<Category>,
        radiusMeters: Double = 1500.0,
        itemsPerCategory: Int = 2
    ) {
        _ui.value = _ui.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val sections = withContext(Dispatchers.IO) {
                    ensurePlaces(context, apiKey)
                    fetchSections(context, apiKey, center, categories, radiusMeters, itemsPerCategory)
                }
                val flat = sections.flatMap { sec ->
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
                    flatItems = flat,
                    error = null
                )
            } catch (e: Exception) {
                _ui.value = RecommendationsUiState(loading = false, error = e.message ?: "Failed")
            }
        }
    }

    // ---------- “Repository” logic inlined ----------

    private fun ensurePlaces(context: Context, apiKey: String) {
        if (!Places.isInitialized()) Places.initialize(context.applicationContext, apiKey)
    }

    private fun fetchSections(
        context: Context,
        apiKey: String,
        center: LatLng,
        categories: List<Category>,
        radiusMeters: Double,
        itemsPerCategory: Int
    ): List<RecommendationSection> {
        val client = Places.createClient(context)
        val fields = listOf(
            Place.Field.NAME,
            Place.Field.PHOTO_METADATAS,
            Place.Field.LAT_LNG,
            Place.Field.TYPES
        )
        val restriction: LocationRestriction = CircularBounds.newInstance(center, radiusMeters)
        val sections = mutableListOf<RecommendationSection>()

        for (cat in categories) {
            val included = categoryToTypes[cat.id] ?: continue
            val req = SearchNearbyRequest.builder(restriction, fields)
                .setIncludedTypes(included)
                .setMaxResultCount(10)
                .setRankPreference(SearchNearbyRequest.RankPreference.POPULARITY)
                .build()

            val resp = try { Tasks.await(client.searchNearby(req)) } catch (_: Exception) { null }

            val items = resp?.places
                ?.take(itemsPerCategory)
                ?.mapNotNull { p ->
                    val title = p.name ?: return@mapNotNull null
                    val coords = p.latLng ?: return@mapNotNull null
                    val distance = haversine(center.latitude, center.longitude, coords.latitude, coords.longitude)
                    val bmp = fetchFirstPhotoBitmap(client, p.photoMetadatas?.firstOrNull())
                    RecommendationItem(title = title, bitmap = bmp, distanceMeters = distance)
                } ?: emptyList()

            if (items.isNotEmpty()) sections += RecommendationSection(cat, items)
        }
        return sections
    }

    private fun fetchFirstPhotoBitmap(
        client: com.google.android.libraries.places.api.net.PlacesClient,
        meta: PhotoMetadata?
    ): Bitmap? {
        return try {
            if (meta == null) return null
            val req = FetchPhotoRequest.builder(meta).setMaxWidth(1200).setMaxHeight(800).build()
            Tasks.await(client.fetchPhoto(req)).bitmap
        } catch (_: Exception) { null }
    }

    /** Haversine (meters) */
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
}
