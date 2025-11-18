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


    private val replyToQuery = mapOf(
        // Refresh
        "Restaurants" to "restaurant",
        "Cafes" to "cafe",
        "Bars" to "bar",
        // Explore
        "Museums" to "museum",
        "Art Gallery" to "art_gallery",
        "Parks" to "park",
        "Attractions" to "tourist_attraction",
        // Entertain
        "Movie Theater" to "movie_theater",
        "Nightlife" to "night_club",
        "Casino" to "casino",
        // ShopStop
        "Malls" to "shopping_mall",
        "Clothing" to "clothing_store",
        "Groceries" to "supermarket",
        // Relax
        "Spa" to "spa",
        "Lodging" to "lodging",
        "Campground" to "campground",
        // Wellbeing
        "Gym" to "gym",
        "Pharmacy" to "pharmacy",
        "Beauty Salon" to "beauty_salon",
        // Emergency
        "Hospitals" to "hospital",
        "Police" to "police",
        "Fire Stations" to "fire_station",
        // Services
        "Post Office" to "post_office",
        "Bank & ATM" to "bank",
        "Gas Station" to "gas_station",
        "Auto Repair" to "car_repair",
    )

    // In ChatViewModel.kt

    // Replace the ENTIRE conversationFlow map with this new one.
    private val conversationFlow = mapOf(
        "START" to ConversationStep(
            botMessages = listOf("Welcome !!!", "Ready to Hoppin?", "Where you wanna Hoppin next?"),
            // Show all top-level categories as the first set of replies
            replies = listOf(
                "Explore", "Refresh", "Entertain", "ShopStop",
                "Relax", "Wellbeing", "Emergency", "Services"
            )
        ),


        "Explore" to ConversationStep(
            botMessages = listOf("Awesome! What kind of place would you like to explore?"),
            replies = listOf("Museums", "Art Gallery", "Parks", "Attractions")
        ),

        "Refresh" to ConversationStep( // Your existing flow, now updated
            botMessages = listOf("Great! Where would you like to get refreshments?"),
            replies = listOf("Restaurants", "Cafes", "Bars")
        ),

        "Entertain" to ConversationStep(
            botMessages = listOf("Time for some fun! What are you in the mood for?"),
            replies = listOf("Movie Theater", "Nightlife", "Casino")
        ),

        "ShopStop" to ConversationStep(
            botMessages = listOf("Ready to shop? What are you looking for?"),
            replies = listOf("Malls", "Clothing", "Groceries")
        ),

        "Relax" to ConversationStep(
            botMessages = listOf("Time to unwind. What sounds best?"),
            replies = listOf("Spa", "Lodging", "Campground")
        ),

        "Wellbeing" to ConversationStep(
            botMessages = listOf("Focusing on wellbeing is a great choice. What do you need?"),
            replies = listOf("Gym", "Pharmacy", "Beauty Salon")
        ),

        "Emergency" to ConversationStep(
            botMessages = listOf("I hope everything is okay. What service do you need immediately?"),
            replies = listOf("Hospitals", "Police", "Fire Stations")
        ),

        "Services" to ConversationStep(
            botMessages = listOf("What kind of service can I help you find?"),
            replies = listOf("Post Office", "Bank & ATM", "Gas Station", "Auto Repair")
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
