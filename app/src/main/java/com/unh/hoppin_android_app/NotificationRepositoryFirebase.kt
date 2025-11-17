package com.unh.hoppin_android_app

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots // This is the correct import
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

object NotificationRepositoryFirebase {
    private val auth = Firebase.auth
    private val db = Firebase.firestore

    // Function to CREATE a new notification for the current user
    suspend fun createNotification(title: String, message: String) {
        val user = auth.currentUser ?: return
        val notificationData = mapOf(
            "title" to title,
            "message" to message,
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("users").document(user.uid)
            .collection("notifications").add(notificationData).await()
    }

    // Function to GET a real-time flow of notifications for the current user
    fun getNotificationsFlow(): Flow<List<AppNotification>> {
        val user = auth.currentUser ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        val query = db.collection("users").document(user.uid)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)

        // The errors should be gone now.
        return query.snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(AppNotification::class.java)?.copy(id = doc.id)
            }
        }
    }
}