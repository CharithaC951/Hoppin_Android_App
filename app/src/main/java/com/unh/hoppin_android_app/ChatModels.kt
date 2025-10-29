package com.unh.hoppin_android_app.chat

import com.google.android.libraries.places.api.model.Place

enum class Author {
    BOT, USER
}

// Add a list of places to the message model
data class ChatMessage(
    val text: String,
    val author: Author,
    val places: List<Place> = emptyList(), // <-- New field
    val isLoading: Boolean = false // <-- New field for showing a loading indicator
)