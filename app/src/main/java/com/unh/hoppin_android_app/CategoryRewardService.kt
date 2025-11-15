package com.unh.hoppin_android_app

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Central place to record category-based rewards when a user visits a place.
 *
 * Responsibilities:
 *  - Dedupe: for a given (uid, date, placeId), only record the visit once per day.
 *  - Maintain per-category visit counts (catX_visits) in /gamification/categoryProgress.
 *  - Maintain per-category badge tiers (catX_badgeTier) based on thresholds.
 *  - Trigger streak updates when a *new* daily visit is recorded.
 */
object CategoryRewardService {

    private val db = FirebaseFirestore.getInstance()
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Shared thresholds (visits) for all categories:
    // 0: <5, 1: >=5, 2: >=25, 3: >=50, 4: >=100, 5: >=250
    private val REWARD_THRESHOLDS = listOf(5, 25, 50, 100, 250)

    /**
     * Compute badge tier from number of visits.
     *
     * 0  -> no badge
     * 1+ -> Bronze (>=5)
     * 2+ -> Silver (>=25)
     * 3+ -> Gold (>=50)
     * 4+ -> Diamond (>=100)
     * 5  -> Platinum (>=250)
     */
    private fun tierForVisits(visits: Int): Int {
        var tier = 0
        for ((index, threshold) in REWARD_THRESHOLDS.withIndex()) {
            if (visits >= threshold) {
                tier = index + 1
            } else {
                break
            }
        }
        return tier
    }

    /**
     * Record a visit to a given place/category for "today" in UTC.
     *
     * - If the same (placeId, date) has already been recorded, nothing happens.
     * - If it is new:
     *     * It creates a daily visit doc for dedupe.
     *     * It increments the category's visit count.
     *     * It updates the category's badge tier if needed.
     *     * It triggers a streak update.
     *
     * @param uid Current Firebase user id.
     * @param placeId Stable Place ID from Places API (or test id).
     * @param categoryId Your app category id (1..8).
     */
    suspend fun recordVisitFor(
        uid: String,
        placeId: String,
        categoryId: Int
    ) {
        if (uid.isBlank() || placeId.isBlank()) return
        if (categoryId !in 1..8) return

        val todayUtc: LocalDate = LocalDate.now(ZoneOffset.UTC)
        val dateStr: String = todayUtc.format(dateFmt)

        val userRef = db.collection("users").document(uid)

        // users/{uid}/gamificationDailyVisits/{yyyy-MM-dd}/places/{placeId}
        val dailyPlaceRef = userRef
            .collection("gamificationDailyVisits")
            .document(dateStr)
            .collection("places")
            .document(placeId)

        // users/{uid}/gamification/categoryProgress
        val progressRef = userRef
            .collection("gamification")
            .document("categoryProgress")

        // Run a transaction so dedupe + increments are atomic.
        // ❗ All reads (tx.get) must be before any writes (tx.set/update).
        val isNewVisit: Boolean = db.runTransaction { tx ->
            // 1) READS (all of them first)
            val dailySnap = tx.get(dailyPlaceRef)
            val progressSnap = tx.get(progressRef)

            // If this place already recorded for this date, do nothing.
            if (dailySnap.exists()) {
                return@runTransaction false
            }

            // 2) Compute new category counters / tier
            val visitsField = "cat${categoryId}_visits"
            val tierField = "cat${categoryId}_badgeTier"

            val oldVisits = (progressSnap.getLong(visitsField) ?: 0L).toInt()
            val newVisits = oldVisits + 1
            val oldTier = (progressSnap.getLong(tierField) ?: 0L).toInt()
            val newTier = tierForVisits(newVisits)

            val updates = mutableMapOf<String, Any>(
                visitsField to newVisits
            )
            if (newTier > oldTier) {
                updates[tierField] = newTier
            }

            // 3) WRITES (after all reads)
            val dailyData = mapOf(
                "placeId" to placeId,
                "categoryId" to categoryId,
                "timestamp" to FieldValue.serverTimestamp()
            )
            tx.set(dailyPlaceRef, dailyData, SetOptions.merge())
            tx.set(progressRef, updates, SetOptions.merge())

            true // brand-new visit recorded
        }.await()

        // If we recorded a brand new visit for today, ensure streak is updated.
        if (isNewVisit) {
            StreakService.dailyCheckInFor(uid)
        }
    }
}
