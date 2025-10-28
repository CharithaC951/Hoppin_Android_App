package com.unh.hoppin_android_app
// Represents the sender of a message
enum class Author {
    BOT, USER
}

// Represents a single message in the chat
data class ChatMessage(
    val text: String,
    val author: Author
)