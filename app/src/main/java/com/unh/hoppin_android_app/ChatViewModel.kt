package com.unh.hoppin_android_app.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
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

    private val conversationFlow = mapOf(
        "START" to ConversationStep(
            botMessages = listOf("Welcome !!!", "Ready to Hoppin?", "where you wanna Hoppin next"),
            replies = listOf("Food and drinks", "Travel", "Hotels")
        ),
        "Food and drinks" to ConversationStep(
            botMessages = listOf("What are you looking under Food and Drinks?"),
            replies = listOf("Restaurants", "Cafes", "Bar & Breweries")
        ),
        "Restaurants" to ConversationStep(
            botMessages = listOf("how do you wanna go with"),
            replies = listOf("Near by", "Popular", "Cuisine Type")
        ),
        "Cuisine Type" to ConversationStep(
            botMessages = listOf("What cuisine you are looking for?"),
            replies = listOf("American", "Mexican", "Indian")
        )
    )

    init {
        startConversation()
    }

    private fun startConversation() {
        val initialStep = conversationFlow["START"]
        initialStep?.let { step ->
            viewModelScope.launch {
                for (message in step.botMessages) {
                    addBotMessage(ChatMessage(message, Author.BOT))
                    delay(800)
                }
                _quickReplies.value = step.replies
            }
        }
    }

    fun onUserReply(reply: String, userLocation: LatLng?) {
        _messages.value += ChatMessage(reply, Author.USER)
        _quickReplies.value = emptyList()

        val finalCuisines = listOf("American", "Mexican", "Indian")
        if (reply in finalCuisines) {
            findPlaces(reply, userLocation)
            return
        }

        val nextStep = conversationFlow[reply]
        viewModelScope.launch {
            delay(1000)
            if (nextStep != null) {
                for (message in nextStep.botMessages) {
                    addBotMessage(ChatMessage(message, Author.BOT))
                    delay(800)
                }
                _quickReplies.value = nextStep.replies
            } else {
                addBotMessage(ChatMessage("Sorry, I can't help with that yet!", Author.BOT))
            }
        }
    }

    private fun findPlaces(cuisine: String, userLocation: LatLng?) {
        if (userLocation == null) {
            addBotMessage(ChatMessage("I can't seem to find your location. Please enable location services and try again.", Author.BOT))
            return
        }

        viewModelScope.launch {
            addBotMessage(ChatMessage("Okay, searching for ${cuisine.lowercase()} restaurants near you...", Author.BOT, isLoading = true))

            val result = placesRepository.searchNearbyPlaces(
                query = "restaurant",
                userLocation = userLocation
            )

            _messages.value = _messages.value.filterNot { it.isLoading }

            result.onSuccess { places ->
                if (places.isNotEmpty()) {
                    addBotMessage(ChatMessage("Here are some places I found:", Author.BOT, places = places))
                } else {
                    addBotMessage(ChatMessage("Sorry, I couldn't find any ${cuisine.lowercase()} restaurants nearby.", Author.BOT))
                }
            }.onFailure {
                addBotMessage(ChatMessage("Oops! Something went wrong while searching. Please try again.", Author.BOT))
            }
        }
    }

    private fun addBotMessage(message: ChatMessage) {
        _messages.value += message
    }
}

data class ConversationStep(val botMessages: List<String>, val replies: List<String>)
