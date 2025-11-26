package com.unh.hoppin_android_app

import com.google.firebase.Timestamp

/**
 * Represents a user-created trip itinerary: a named collection of places.
 *
 * Stored in:
 *  - /users/{uid}/itineraries/{itineraryId}
 *  - /itineraries_all/{itineraryId}
 */
data class TripItinerary(
    val id: String = "",           // Firestore document ID
    val userId: String = "",       // Owner UID
    val name: String = "",         // User-visible name ("NYC Food Crawl")
    val description: String = "",  // Optional short description
    val placeIds: List<String> = emptyList(), // List of Google Place IDs
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
)
