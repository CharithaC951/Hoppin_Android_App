package com.unh.hoppin_android_app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CategoryProgressState(
    val visits: Map<Int, Int> = emptyMap(),   // categoryId -> count of distinct visits
    val tiers: Map<Int, Int> = emptyMap(),    // categoryId -> badge tier (0..5)
    val isLoading: Boolean = true
)

/**
 * Listens to /users/{uid}/gamification/categoryProgress and exposes
 * a simple state for the UI: per-category visit counts and badge tiers.
 */
class CategoryProgressViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow(CategoryProgressState(isLoading = true))
    val state: StateFlow<CategoryProgressState> = _state.asStateFlow()

    init {
        observeAuthAndDoc()
    }

    private fun observeAuthAndDoc() {
        val authFlow: Flow<String?> = callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { fa ->
                trySend(fa.currentUser?.uid)
            }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }.distinctUntilChanged()

        viewModelScope.launch {
            authFlow
                .flatMapLatest { uid ->
                    if (uid == null) {
                        flowOf(CategoryProgressState(isLoading = false))
                    } else {
                        callbackFlow {
                            val ref = db.collection("users")
                                .document(uid)
                                .collection("gamification")
                                .document("categoryProgress")

                            val reg = ref.addSnapshotListener { snap, e ->
                                if (e != null) return@addSnapshotListener

                                if (snap == null || !snap.exists()) {
                                    trySend(CategoryProgressState(isLoading = false))
                                } else {
                                    val visits = mutableMapOf<Int, Int>()
                                    val tiers = mutableMapOf<Int, Int>()

                                    for (catId in 1..8) {
                                        val v = (snap.getLong("cat${catId}_visits") ?: 0L).toInt()
                                        val t = (snap.getLong("cat${catId}_badgeTier") ?: 0L).toInt()
                                        visits[catId] = v
                                        tiers[catId] = t
                                    }

                                    trySend(
                                        CategoryProgressState(
                                            visits = visits,
                                            tiers = tiers,
                                            isLoading = false
                                        )
                                    )
                                }
                            }
                            awaitClose { reg.remove() }
                        }
                    }
                }
                .collect { _state.value = it }
        }
    }
}
