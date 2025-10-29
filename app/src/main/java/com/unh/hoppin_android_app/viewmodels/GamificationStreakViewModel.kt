package com.unh.hoppin_android_app.viewmodels

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
    val lastCheckInDate: String? = null
)

class GamificationStreakViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private fun uid(): String =
        requireNotNull(auth.currentUser?.uid) { "User must be signed in" }

    private fun stateDoc() =
        db.collection("users").document(uid())
            .collection("gamification").document("state")

    private val _streak = MutableStateFlow(StreakState())
    val streak: StateFlow<StreakState> = _streak.asStateFlow()

    init { observeState() }

    private fun observeState() {
        val flow = callbackFlow<StreakState> {
            val reg = stateDoc().addSnapshotListener { snap, e ->
                if (e != null) return@addSnapshotListener
                if (snap == null || !snap.exists()) {
                    stateDoc().set(
                        mapOf(
                            "currentStreak" to 0,
                            "bestStreak" to 0,
                            "lastCheckInDate" to null
                        ),
                        SetOptions.merge()
                    )
                    trySend(StreakState())
                } else {
                    val cur = (snap.getLong("currentStreak") ?: 0L).toInt()
                    val best = (snap.getLong("bestStreak") ?: 0L).toInt()
                    val last = snap.getString("lastCheckInDate")
                    trySend(StreakState(cur, best, last))
                }
            }
            awaitClose { reg.remove() }
        }
        viewModelScope.launch { flow.collect { _streak.value = it } }
    }

    fun dailyCheckIn(onResult: (before: StreakState, after: StreakState) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            db.runTransaction { tx ->
                val ref = stateDoc()
                val snap = tx.get(ref)

                val cur = (snap.getLong("currentStreak") ?: 0L).toInt()
                val best = (snap.getLong("bestStreak") ?: 0L).toInt()
                val last = snap.getString("lastCheckInDate")

                val today = LocalDate.now(ZoneOffset.UTC)
                val yesterday = today.minusDays(1)
                val todayStr = today.format(dateFmt)

                val before = StreakState(cur, best, last)
                val after: StreakState
                val update = when (last) {
                    todayStr -> {
                        after = before
                        emptyMap()
                    }
                    yesterday.format(dateFmt) -> {
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
}
