package com.unh.hoppin_android_app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StreakState(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastCheckInDate: String? = null,
    val isLoading: Boolean = false
)

class GamificationStreakViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _streak = MutableStateFlow(StreakState(isLoading = true))
    val streak: StateFlow<StreakState> = _streak.asStateFlow()

    init {
        observeAuthAndDoc()
    }

    private fun observeAuthAndDoc() {
        val authFlow: Flow<String?> = callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }.distinctUntilChanged()

        viewModelScope.launch {
            authFlow
                .flatMapLatest { uid ->
                    if (uid == null) {
                        flowOf(StreakState(isLoading = false))
                    } else {
                        callbackFlow {
                            val ref = db.collection("users").document(uid)
                                .collection("gamification").document("state")
                            val reg = ref.addSnapshotListener { snap, e ->
                                if (e != null) return@addSnapshotListener
                                if (snap == null || !snap.exists()) {
                                    trySend(StreakState(isLoading = false))
                                } else {
                                    val cur = (snap.getLong("currentStreak") ?: 0L).toInt()
                                    val best = (snap.getLong("bestStreak") ?: 0L).toInt()
                                    val last = snap.getString("lastCheckInDate")
                                    trySend(StreakState(cur, best, last, isLoading = false))
                                }
                            }
                            awaitClose { reg.remove() }
                        }
                    }
                }
                .collect { _streak.value = it }
        }
    }
}
