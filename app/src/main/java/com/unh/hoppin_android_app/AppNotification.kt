// Create new file: AppNotification.kt
package com.unh.hoppin_android_app

import com.google.firebase.Timestamp

data class AppNotification(
    val id: String = "", // Firestore document ID
    val title: String = "",
    val message: String = "",
    val timestamp: Timestamp = Timestamp.now()
)