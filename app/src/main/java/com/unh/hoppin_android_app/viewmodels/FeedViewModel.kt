package com.unh.hoppin_android_app.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SharedItinerary(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val placeIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null
)

data class CommonReview(
    val id: String = "",
    val placeId: String = "",
    val placeName: String = "",
    val author: String = "",
    val rating: Int = 0,
    val text: String = "",
    val createdAt: Timestamp? = null
)

data class FeedUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val itineraries: List<SharedItinerary> = emptyList(),
    val reviews: List<CommonReview> = emptyList()
)

class FeedViewModel : ViewModel() {

    private val db = Firebase.firestore

    companion object {
        private const val ITINERARY_LIMIT = 20
        private const val REVIEW_LIMIT = 50
        private const val TAG = "FeedViewModel"
    }

    private val _uiState = MutableStateFlow(FeedUiState(loading = true))
    val uiState: StateFlow<FeedUiState> = _uiState

    init {
        loadFeed()
    }

    fun refresh() = loadFeed()

    // ------------------------------------------------------------------------
    // Public loader: cache-first + parallel server fetch
    // ------------------------------------------------------------------------
    private fun loadFeed() {
        viewModelScope.launch {
            // show spinner only if we don't already have data
            val hasExistingData =
                _uiState.value.itineraries.isNotEmpty() || _uiState.value.reviews.isNotEmpty()

            _uiState.value = _uiState.value.copy(
                loading = !hasExistingData,
                error = null
            )

            try {
                // 1) Try to serve from CACHE quickly (if available)
                try {
                    val cached = fetchFeed(Source.CACHE)
                    if (cached.itineraries.isNotEmpty() || cached.reviews.isNotEmpty()) {
                        _uiState.value = FeedUiState(
                            loading = false,
                            error = null,
                            itineraries = cached.itineraries,
                            reviews = cached.reviews
                        )
                    }
                } catch (cacheErr: Exception) {
                    // It's okay if cache is empty / missing – we just fall back to server
                    Log.d(TAG, "No cache available or cache fetch failed: ${cacheErr.localizedMessage}")
                }

                // 2) Always refresh from SERVER in parallel (itineraries + reviews)
                val fresh = fetchFeed(Source.SERVER)

                _uiState.value = FeedUiState(
                    loading = false,
                    error = null,
                    itineraries = fresh.itineraries,
                    reviews = fresh.reviews
                )

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load feed", e)
                _uiState.value = FeedUiState(
                    loading = false,
                    error = e.localizedMessage ?: "Failed to load feed",
                    itineraries = emptyList(),
                    reviews = emptyList()
                )
            }
        }
    }

    // ------------------------------------------------------------------------
    // Internal payload + parallel fetch function
    // ------------------------------------------------------------------------
    private data class FeedPayload(
        val itineraries: List<SharedItinerary>,
        val reviews: List<CommonReview>
    )

    private suspend fun fetchFeed(source: Source): FeedPayload {
        // Run both Firestore reads in parallel
        val itinerariesDeferred = viewModelScope.async {
            fetchItineraries(source)
        }
        val reviewsDeferred = viewModelScope.async {
            fetchReviews(source)
        }

        val itineraries = itinerariesDeferred.await()
        val reviews = reviewsDeferred.await()

        return FeedPayload(itineraries, reviews)
    }

    private suspend fun fetchItineraries(source: Source): List<SharedItinerary> {
        val itSnap = db.collection("itineraries_all")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(ITINERARY_LIMIT.toLong())
            .get(source)
            .await()

        return itSnap.documents.mapNotNull { doc ->
            val placeIds = (doc.get("placeIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            SharedItinerary(
                id = doc.id,
                userId = doc.getString("userId") ?: "",
                name = doc.getString("name") ?: "",
                description = doc.getString("description") ?: "",
                placeIds = placeIds,
                createdAt = doc.getTimestamp("createdAt")
            )
        }
    }

    private suspend fun fetchReviews(source: Source): List<CommonReview> {
        val revSnap = db.collection("reviews_all")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(REVIEW_LIMIT.toLong())
            .get(source)
            .await()

        return revSnap.documents.mapNotNull { doc ->
            CommonReview(
                id = doc.id,
                placeId = doc.getString("placeId") ?: "",
                placeName = doc.getString("placeName") ?: "",
                author = doc.getString("author") ?: "Anonymous",
                rating = (doc.getLong("rating") ?: 0L).toInt(),
                text = doc.getString("text") ?: "",
                createdAt = doc.getTimestamp("createdAt")
            )
        }
    }
}
