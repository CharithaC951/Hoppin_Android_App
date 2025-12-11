package com.unh.hoppin_android_app

import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Handles CRUD operations for trip itineraries in Firestore.
 *
 * Per-user:
 *   /users/{uid}/itineraries/{itineraryId}
 *
 * Global (all users):
 *   /itineraries_all/{itineraryId}
 */
object TripItineraryRepositoryFirebase {

    private const val COLLECTION_USERS = "users"
    private const val SUBCOLL_ITINERARIES = "itineraries"
    private const val COLLECTION_ALL_ITINERARIES = "itineraries_all"

    private val auth = Firebase.auth
    private val db = Firebase.firestore

    // ---------- Reads ----------

    /**
     * Live stream of the current user's itineraries, ordered by createdAt DESC.
     */
    fun userItinerariesFlow(): Flow<List<TripItinerary>> {
        val user = auth.currentUser ?: return flowOf(emptyList())

        val query = db.collection(COLLECTION_USERS)
            .document(user.uid)
            .collection(SUBCOLL_ITINERARIES)
            .orderBy("createdAt", Query.Direction.DESCENDING)

        return query.snapshots().map { snap ->
            snap.documents.mapNotNull { doc ->
                doc.toObject(TripItinerary::class.java)?.copy(id = doc.id)
            }
        }
    }

    /**
     * Fetch a single itinerary for the current user (throws if not logged in).
     */
    suspend fun getItinerary(itineraryId: String): TripItinerary? {
        val user = auth.currentUser ?: throw IllegalStateException("User not logged in")
        val doc = db.collection(COLLECTION_USERS)
            .document(user.uid)
            .collection(SUBCOLL_ITINERARIES)
            .document(itineraryId)
            .get()
            .await()

        return doc.toObject(TripItinerary::class.java)?.copy(id = doc.id)
    }

    // ---------- Creates / Updates ----------

    /**
     * Create a new itinerary for the current user and mirror it in /itineraries_all.
     *
     * @return the new itinerary ID.
     */
    suspend fun createItinerary(
        name: String,
        description: String = "",
        placeIds: List<String> = emptyList()
    ): String {
        val user = auth.currentUser ?: throw IllegalStateException("User not logged in")

        val userColl = db.collection(COLLECTION_USERS)
            .document(user.uid)
            .collection(SUBCOLL_ITINERARIES)

        val docRef = userColl.document() // auto ID
        val now = Timestamp.now()

        val itinerary = TripItinerary(
            id = docRef.id,
            userId = user.uid,
            name = name,
            description = description,
            placeIds = placeIds,
            createdAt = now,
            updatedAt = now
        )

        // Write to user subcollection
        docRef.set(itinerary).await()

        // Mirror to global collection
        db.collection(COLLECTION_ALL_ITINERARIES)
            .document(itinerary.id)
            .set(itinerary)
            .await()

        return itinerary.id
    }

    /**
     * Update the main properties of an itinerary (name/description/placeIds).
     * You pass the fully-updated object and we overwrite in both locations.
     */
    suspend fun saveItinerary(itinerary: TripItinerary) {
        if (itinerary.id.isBlank()) {
            throw IllegalArgumentException("Itinerary must have an id before saving")
        }

        val user = auth.currentUser ?: throw IllegalStateException("User not logged in")

        val updated = itinerary.copy(updatedAt = Timestamp.now())

        // User copy
        db.collection(COLLECTION_USERS)
            .document(user.uid)
            .collection(SUBCOLL_ITINERARIES)
            .document(updated.id)
            .set(updated)
            .await()

        // Global copy
        db.collection(COLLECTION_ALL_ITINERARIES)
            .document(updated.id)
            .set(updated)
            .await()
    }

    // ---------- Place operations (add / remove) ----------

    /**
     * Append a placeId to an itinerary's placeIds (idempotent).
     */
    suspend fun addPlaceToItinerary(itineraryId: String, placeId: String) {
        val user = auth.currentUser ?: throw IllegalStateException("User not logged in")
        val now = Timestamp.now()
        val updates = mapOf(
            "placeIds" to FieldValue.arrayUnion(placeId),
            "updatedAt" to now
        )

        // User copy
        db.collection(COLLECTION_USERS)
            .document(user.uid)
            .collection(SUBCOLL_ITINERARIES)
            .document(itineraryId)
            .update(updates)
            .await()

        // Global copy
        db.collection(COLLECTION_ALL_ITINERARIES)
            .document(itineraryId)
            .update(updates)
            .await()
    }

    /**
     * Remove a placeId from an itinerary's placeIds (idempotent).
     */
    suspend fun removePlaceFromItinerary(itineraryId: String, placeId: String) {
        val user = auth.currentUser ?: throw IllegalStateException("User not logged in")
        val now = Timestamp.now()
        val updates = mapOf(
            "placeIds" to FieldValue.arrayRemove(placeId),
            "updatedAt" to now
        )

        // User copy
        db.collection(COLLECTION_USERS)
            .document(user.uid)
            .collection(SUBCOLL_ITINERARIES)
            .document(itineraryId)
            .update(updates)
            .await()

        // Global copy
        db.collection(COLLECTION_ALL_ITINERARIES)
            .document(itineraryId)
            .update(updates)
            .await()
    }

    // ---------- Deletes ----------

    /**
     * Delete an itinerary from the user and global collection.
     */
    suspend fun deleteItinerary(itineraryId: String) {
        val user = auth.currentUser ?: throw IllegalStateException("User not logged in")

        // User copy
        db.collection(COLLECTION_USERS)
            .document(user.uid)
            .collection(SUBCOLL_ITINERARIES)
            .document(itineraryId)
            .delete()
            .await()

        // Global copy
        db.collection(COLLECTION_ALL_ITINERARIES)
            .document(itineraryId)
            .delete()
            .await()
    }

    // Read from /itineraries_all (no need for current user's uid)
    suspend fun getGlobalItinerary(itineraryId: String): TripItinerary? {
        val doc = db.collection(COLLECTION_ALL_ITINERARIES)
            .document(itineraryId)
            .get()
            .await()

        return doc.toObject(TripItinerary::class.java)?.copy(id = doc.id)
    }

}
