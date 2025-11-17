package com.unh.hoppin_android_app.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.unh.hoppin_android_app.Author
import com.unh.hoppin_android_app.ChatMessage
import com.unh.hoppin_android_app.chat.PlacesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _quickReplies = MutableStateFlow<List<String>>(emptyList())
    val quickReplies = _quickReplies.asStateFlow()

    private val placesRepository = PlacesRepository(application.applicationContext)

    // Map replies → Places API types
    private val replyToQuery = mapOf(
        "Restaurants" to "restaurant",
        "Cafes" to "cafe",
        "Bar & Breweries" to "bar",
        "Near by" to "restaurant",
        "Popular" to "restaurant",
        "Cuisine Type" to "restaurant",
        "American" to "american restaurant",
        "Mexican" to "mexican restaurant",
        "Indian" to "indian restaurant"
    )

    private val conversationFlow = mapOf(
        "START" to ConversationStep(
            botMessages = listOf("Welcome !!!", "Ready to Hoppin?", "Where you wanna Hoppin next?"),
            replies = listOf("Explore", "Refresh", "Relax")
        ),
        "Refresh" to ConversationStep(
            botMessages = listOf("What are you looking for under Refresh?"),
            replies = listOf("Restaurants", "Cafes", "Bar & Breweries")
        ),
        "Restaurants" to ConversationStep(
            botMessages = listOf("How do you want to go with?"),
            replies = listOf("Near by", "Popular", "Cuisine Type")
        ),
        "Cuisine Type" to ConversationStep(
            botMessages = listOf("What cuisine are you looking for?"),
            replies = listOf("American", "Mexican", "Indian")
        )
    )

    init {
        startConversation()
    }

    private fun startConversation() {
        val first = conversationFlow["START"] ?: return
        viewModelScope.launch {
            for (msg in first.botMessages) {
                addBotMessage(ChatMessage(msg, Author.BOT))
                delay(700)
            }
            _quickReplies.value = first.replies
        }
    }

    fun onUserReply(reply: String, userLocation: LatLng?) {
        _messages.value += ChatMessage(reply, Author.USER)
        _quickReplies.value = emptyList()

        // If the reply should trigger a Places API search
        if (replyToQuery.containsKey(reply)) {
            val type = replyToQuery[reply]!!
            searchPlaces(type, reply, userLocation)
            return
        }

        // Otherwise go to normal flow
        val next = conversationFlow[reply]
        viewModelScope.launch {
            delay(600)
            if (next != null) {
                for (msg in next.botMessages) {
                    addBotMessage(ChatMessage(msg, Author.BOT))
                    delay(600)
                }
                _quickReplies.value = next.replies
            } else {
                addBotMessage(ChatMessage("I can't help with that yet!", Author.BOT))
            }
        }
    }

    private fun searchPlaces(query: String, displayName: String, userLocation: LatLng?) {
        if (userLocation == null) {
            addBotMessage(
                ChatMessage(
                    "Couldn't detect location. Please enable location services.",
                    Author.BOT
                )
            )
            return
        }

        viewModelScope.launch {
            // loading bubble
            addBotMessage(
                ChatMessage(
                    text = "Searching for $displayName near you...",
                    author = Author.BOT,
                    isLoading = true
                )
            )

            val result = placesRepository.searchNearbyPlaces(query, userLocation)

            // Remove loading bubble
            _messages.value = _messages.value.filterNot { it.isLoading }

            result.onSuccess { places ->
                if (places.isEmpty()) {
                    addBotMessage(
                        ChatMessage(
                            text = "No $displayName found nearby.",
                            author = Author.BOT
                        )
                    )
                } else {
                    addBotMessage(
                        ChatMessage(
                            text = "Here are some $displayName:",
                            author = Author.BOT,
                            places = places
                        )
                    )
                }
            }.onFailure {
                addBotMessage(
                    ChatMessage(
                        text = "Something went wrong while searching for $displayName.",
                        author = Author.BOT
                    )
                )
            }
        }
    }

    private fun addBotMessage(message: ChatMessage) {
        _messages.value += message
    }
}

data class ConversationStep(
    val botMessages: List<String>,
    val replies: List<String>
)
