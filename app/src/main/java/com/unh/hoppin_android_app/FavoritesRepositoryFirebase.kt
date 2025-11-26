package com.unh.hoppin_android_app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object FavoritesRepositoryFirebase {

    private const val KEY_FAVORITES = "favoritePlaceIds"

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private fun userDoc() = db.collection("users").document(requireUid())

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")

    /** Live stream of favourite place IDs */
    fun favoriteIdsFlow(): Flow<Set<String>> = callbackFlow {
        val reg = userDoc().addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptySet())
                return@addSnapshotListener
            }
            val ids = snap?.get(KEY_FAVORITES) as? List<*> ?: emptyList<String>()
            trySend(ids.filterIsInstance<String>().toSet())
        }
        awaitClose { reg.remove() }
    }

    /** Add one ID (idempotent). */
    suspend fun add(placeId: String) {
        userDoc()
            .set(mapOf(KEY_FAVORITES to FieldValue.arrayUnion(placeId)), SetOptions.merge())
            .await()
    }

    /** Remove one ID (idempotent). */
    suspend fun remove(placeId: String) {
        userDoc()
            .set(mapOf(KEY_FAVORITES to FieldValue.arrayRemove(placeId)), SetOptions.merge())
            .await()
    }

    /** Toggle favourite state for a given placeId. */
    suspend fun toggle(placeId: String) {
        val before = (userDoc().get().await().get(KEY_FAVORITES) as? List<*>)
            ?.filterIsInstance<String>()
            ?.toSet()
            ?: emptySet()

        if (before.contains(placeId)) {
            remove(placeId)
        } else {
            add(placeId)
        }
    }
}
