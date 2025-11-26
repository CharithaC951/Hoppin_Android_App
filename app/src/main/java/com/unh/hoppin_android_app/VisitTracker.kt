package com.unh.hoppin_android_app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * VisitTracker:
 *  - Periodically samples device location.
 *  - Uses Places "current place" to identify nearby POIs.
 *  - If the user stays at the *same place* for at least DWELL_MS, we record a visit
 *    via CategoryRewardService.
 *
 * In DEBUG builds, DWELL_MS is ~20 seconds for easy testing.
 * In RELEASE builds, DWELL_MS is 5 minutes.
 */
class VisitTracker(
    private val context: Context,
    private val fusedClient: FusedLocationProviderClient
) {

    companion object {
        // 🧪 Dwell time tuning
        private const val PROD_DWELL_MS = 5 * 60 * 1000L      // 5 minutes
        private const val DEBUG_DWELL_MS = 20 * 1000L         // 20 seconds

        // Use short dwell in debug builds, long dwell in release
        private val DWELL_MS: Long =
            if (BuildConfig.DEBUG) DEBUG_DWELL_MS else PROD_DWELL_MS

        // How often we ask for location
        private const val LOCATION_INTERVAL_MS = 10_000L      // 10s
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val placesClient: PlacesClient = Places.createClient(context)

    private var started = false

    private var currentCandidate: DwellCandidate? = null

    // Simple holder for current dwell state
    private data class DwellCandidate(
        val placeId: String,
        val categoryId: Int,
        val placeLatLng: LatLng,
        val firstSeenAtMs: Long,
        var hasRecorded: Boolean
    )

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val latLng = LatLng(loc.latitude, loc.longitude)
            handleLocationSample(latLng)
        }
    }

    @SuppressLint("MissingPermission") // You already gate this with runtime permission
    fun start() {
        if (started) return
        started = true

        val req = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LOCATION_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS / 2)
            .setWaitForAccurateLocation(false)
            .build()

        fusedClient.requestLocationUpdates(
            req,
            locationCallback,
            Looper.getMainLooper()
        )

        Log.d("VisitTracker", "VisitTracker started; dwellMs=$DWELL_MS")
    }

    fun stop() {
        if (!started) return
        started = false
        fusedClient.removeLocationUpdates(locationCallback)
        Log.d("VisitTracker", "VisitTracker stopped")
    }

    /**
     * Called every time we get a new location sample.
     * We ask Places for the most likely current place around this point.
     */
    private fun handleLocationSample(latLng: LatLng) {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG,
            Place.Field.TYPES
        )
        val request = FindCurrentPlaceRequest.newInstance(fields)

        try {
            placesClient.findCurrentPlace(request)
                .addOnSuccessListener { response ->
                    val top = response.placeLikelihoods.maxByOrNull { it.likelihood }
                        ?: return@addOnSuccessListener
                    val place = top.place

                    val categoryId = mapPlaceToCategoryId(place) ?: return@addOnSuccessListener
                    processCandidate(place, categoryId)
                }
                .addOnFailureListener { e ->
                    Log.w("VisitTracker", "findCurrentPlace failed: ${e.message}")
                }
        } catch (e: SecurityException) {
            // location permission missing; nothing to do
            Log.w("VisitTracker", "Missing location permission for findCurrentPlace")
        } catch (e: Exception) {
            Log.e("VisitTracker", "Unexpected error in handleLocationSample", e)
        }
    }

    /**
     * Given a Place and mapped categoryId, check dwell time and trigger rewards.
     */
    private fun processCandidate(place: Place, categoryId: Int) {
        val now = System.currentTimeMillis()
        val id = place.id ?: return
        val latLng = place.latLng ?: return

        val current = currentCandidate

        // If we changed to a different place, start a new candidate window.
        if (current == null || current.placeId != id) {
            currentCandidate = DwellCandidate(
                placeId = id,
                categoryId = categoryId,
                placeLatLng = latLng,
                firstSeenAtMs = now,
                hasRecorded = false
            )
            Log.d("VisitTracker", "New candidate place=$id cat=$categoryId at $latLng")
            return
        }

        // Still at the same place
        val elapsed = now - current.firstSeenAtMs

        // Already recorded for this dwell window
        if (current.hasRecorded) return

        if (elapsed >= DWELL_MS) {
            // Dwell threshold reached; mark and record visit.
            current.hasRecorded = true
            Log.d(
                "VisitTracker",
                "Dwell reached for place=${current.placeId} cat=${current.categoryId}, elapsed=${elapsed}ms"
            )

            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

            scope.launch {
                try {
                    CategoryRewardService.recordVisitFor(
                        uid = uid,
                        placeId = current.placeId,
                        categoryId = current.categoryId
                    )
                    Log.d("VisitTracker", "Recorded visit in CategoryRewardService")
                } catch (e: Exception) {
                    Log.e("VisitTracker", "Error recording visit", e)
                }
            }
        }
    }

    /**
     * Map Places API types into your 8 high-level categories.
     *
     *  1: Explore
     *  2: Refresh
     *  3: Entertain
     *  4: ShopStop
     *  5: Relax
     *  6: Wellbeing
     *  7: Emergency
     *  8: Services
     */
    private fun mapPlaceToCategoryId(place: Place): Int? {
        val types = place.types ?: return null
        val typeNames = types.map { it.name.lowercase() }

        return when {
            // Category 1 – Explore
            typeNames.any {
                it.contains("tourist_attraction") ||
                        it.contains("museum") ||
                        it.contains("art_gallery") ||
                        it.contains("park")
            } -> 1

            // Category 2 – Refresh
            typeNames.any {
                it.contains("restaurant") ||
                        it.contains("cafe") ||
                        it.contains("bar") ||
                        it.contains("bakery")
            } -> 2

            // Category 3 – Entertain
            typeNames.any {
                it.contains("movie_theater") ||
                        it.contains("night_club") ||
                        it.contains("bowling_alley") ||
                        it.contains("casino")
            } -> 3

            // Category 4 – ShopStop
            typeNames.any {
                it.contains("shopping_mall") ||
                        it.contains("clothing_store") ||
                        it.contains("department_store") ||
                        it == "store" ||
                        it.contains("supermarket")
            } -> 4

            // Category 5 – Relax
            typeNames.any {
                it.contains("spa") ||
                        it.contains("lodging") ||
                        it.contains("campground")
            } -> 5

            // Category 6 – Wellbeing
            typeNames.any {
                it.contains("gym") ||
                        it.contains("pharmacy") ||
                        it.contains("doctor") ||
                        it.contains("beauty_salon")
            } -> 6

            // Category 7 – Emergency
            typeNames.any {
                it.contains("hospital") ||
                        it.contains("police") ||
                        it.contains("fire_station")
            } -> 7

            // Category 8 – Services
            typeNames.any {
                it.contains("post_office") ||
                        it.contains("bank") ||
                        it == "atm" ||
                        it.contains("gas_station") ||
                        it.contains("car_repair")
            } -> 8

            else -> null
        }
    }
}
