package com.unh.hoppin_android_app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.coroutines.resumeWithException

data class FavoriteDoc(
    val placeId: String = "",
    val createdAt: com.google.firebase.Timestamp? = null
)

object FavoritesRepositoryFirebase {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private fun favsCol() = db.collection("users")
        .document(requireNotNull(auth.currentUser?.uid) { "User must be signed in" })
        .collection("favorites")

    fun favoriteIdsFlow(): Flow<Set<String>> = callbackFlow {
        val reg = favsCol()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptySet()); return@addSnapshotListener }
                val ids = snap?.documents?.mapNotNull { it.getString("placeId") }?.toSet().orEmpty()
                trySend(ids)
            }
        awaitClose { reg.remove() }
    }

    suspend fun toggle(placeId: String) {
        val docRef = favsCol().document(placeId)
        val snap = docRef.get().await()
        if (snap.exists()) {
            docRef.delete().await()
        } else {
            docRef.set(
                mapOf(
                    "placeId" to placeId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun isFavorite(placeId: String): Boolean =
        favsCol().document(placeId).get().await().exists()
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result, null)
            else cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }
