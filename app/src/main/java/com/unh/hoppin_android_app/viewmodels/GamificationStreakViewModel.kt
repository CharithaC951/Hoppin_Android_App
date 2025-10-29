package com.unh.hoppin_android_app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class StreakState(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val lastCheckInDate: String? = null, // "yyyy-MM-dd" (UTC)
    val isLoading: Boolean = false
)

class GamificationStreakViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _streak = MutableStateFlow(StreakState(isLoading = true))
    val streak: StateFlow<StreakState> = _streak.asStateFlow()

    // Guard so we don't run auto check-in more than once per (uid, day)
    private var lastAutoKey: String? = null

    init {
        // When auth user changes, (re)subscribe to their streak doc
        observeAuthAndDoc()
    }

    private fun observeAuthAndDoc() {
        // Auth state as a flow
        val authFlow = callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }

        viewModelScope.launch {
            authFlow.collect { user ->
                if (user == null) {
                    _streak.value = StreakState(isLoading = false)
                    return@collect
                }

                // Realtime listener on /users/{uid}/gamification/state
                val docRef = db.collection("users").document(user.uid)
                    .collection("gamification").document("state")

                val snapFlow = callbackFlow {
                    val reg = docRef.addSnapshotListener { snap, e ->
                        if (e != null) return@addSnapshotListener
                        if (snap == null || !snap.exists()) {
                            // Initialize lazily (no write if user never opens streak UI)
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

                // Collect the doc changes
                snapFlow.collect { st ->
                    _streak.value = st
                    // Trigger auto check-in once per UTC day after we have a snapshot
                    ensureTodayCheckIn()
                }
            }
        }
    }

    /** Transactional, idempotent daily check-in (UTC). Safe to call any time. */
    fun dailyCheckIn(onResult: (before: StreakState, after: StreakState) -> Unit = { _, _ -> }) {
        val user = auth.currentUser ?: return
        val ref = db.collection("users").document(user.uid)
            .collection("gamification").document("state")

        viewModelScope.launch {
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                val cur = (snap.getLong("currentStreak") ?: 0L).toInt()
                val best = (snap.getLong("bestStreak") ?: 0L).toInt()
                val last = snap.getString("lastCheckInDate")

                val today = LocalDate.now(ZoneOffset.UTC)
                val yesterday = today.minusDays(1)
                val todayStr = today.format(dateFmt)
                val yStr = yesterday.format(dateFmt)

                val before = StreakState(cur, best, last)
                val after: StreakState
                val update = when (last) {
                    todayStr -> {
                        after = before
                        emptyMap()
                    }
                    yStr -> {
                        val newCur = cur + 1
                        val newBest = maxOf(best, newCur)
                        after = StreakState(newCur, newBest, todayStr)
                        mapOf(
                            "currentStreak" to newCur,
                            "bestStreak" to newBest,
                            "lastCheckInDate" to todayStr
                        )
                    }
                    else -> {
                        after = StreakState(1, maxOf(best, 1), todayStr)
                        mapOf(
                            "currentStreak" to 1,
                            "bestStreak" to maxOf(best, 1),
                            "lastCheckInDate" to todayStr
                        )
                    }
                }

                if (update.isNotEmpty()) {
                    tx.set(ref, update, SetOptions.merge())
                }
                onResult(before, after)
            }
        }
    }

    /** Ensures we auto-check-in once per (uid, UTC day) after we have a doc snapshot. */
    fun ensureTodayCheckIn() {
        val user = auth.currentUser ?: return
        val todayStr = LocalDate.now(ZoneOffset.UTC).format(dateFmt)
        val key = "${user.uid}::$todayStr"
        if (lastAutoKey == key) return // already attempted this session today

        lastAutoKey = key
        dailyCheckIn()
    }
}
