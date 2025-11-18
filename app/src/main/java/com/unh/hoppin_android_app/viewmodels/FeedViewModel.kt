package com.unh.hoppin_android_app.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
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

    private val _uiState = MutableStateFlow(FeedUiState(loading = true))
    val uiState: StateFlow<FeedUiState> = _uiState

    init {
        loadFeed()
    }

    fun refresh() = loadFeed()

    private fun loadFeed() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                // 1) Shared itineraries (most recent 20)
                val itSnap = db.collection("itineraries_all")
                    .orderBy("createdAt")
                    .limitToLast(20)
                    .get()
                    .await()

                val itineraries = itSnap.documents.mapNotNull { doc ->
                    val placeIds = (doc.get("placeIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    SharedItinerary(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        name = doc.getString("name") ?: "",
                        description = doc.getString("description") ?: "",
                        placeIds = placeIds,
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }

                // 2) Global reviews (most recent 50)
                val revSnap = db.collection("reviews_all")
                    .orderBy("createdAt")
                    .limitToLast(50)
                    .get()
                    .await()

                val reviews = revSnap.documents.mapNotNull { doc ->
                    CommonReview(
                        id = doc.id,
                        placeId = doc.getString("placeId") ?: "",
                        placeName = doc.getString("placeName") ?: "",
                        author = doc.getString("author") ?: "Anonymous",
                        rating = (doc.getLong("rating") ?: 0L).toInt(),
                        text = doc.getString("text") ?: "",
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }

                _uiState.value = FeedUiState(
                    loading = false,
                    error = null,
                    itineraries = itineraries,
                    reviews = reviews
                )
            } catch (e: Exception) {
                Log.e("FeedViewModel", "Failed to load feed", e)
                _uiState.value = FeedUiState(
                    loading = false,
                    error = e.localizedMessage ?: "Failed to load feed",
                    itineraries = emptyList(),
                    reviews = emptyList()
                )
            }
        }
    }
}
