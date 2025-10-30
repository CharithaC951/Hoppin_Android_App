package com.unh.hoppin_android_app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object StreakService {
    private val db = FirebaseFirestore.getInstance()
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    suspend fun dailyCheckInFor(uid: String) {
        val ref = db.collection("users").document(uid)
            .collection("gamification").document("state")

        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val cur = (snap.getLong("currentStreak") ?: 0L).toInt()
            val best = (snap.getLong("bestStreak") ?: 0L).toInt()
            val last = snap.getString("lastCheckInDate")

            val today = LocalDate.now(ZoneOffset.UTC)
            val yesterday = today.minusDays(1)
            val todayStr = today.format(dateFmt)
            val yStr = yesterday.format(dateFmt)

            val update: Map<String, Any> = when (last) {
                todayStr -> emptyMap()
                yStr -> {
                    val newCur = cur + 1
                    val newBest = maxOf(best, newCur)
                    mapOf("currentStreak" to newCur, "bestStreak" to newBest, "lastCheckInDate" to todayStr)
                }
                else -> {
                    mapOf("currentStreak" to 1, "bestStreak" to maxOf(best, 1), "lastCheckInDate" to todayStr)
                }
            }

            if (update.isNotEmpty()) {
                tx.set(ref, update, SetOptions.merge())
            }
        }.await()
    }
}
