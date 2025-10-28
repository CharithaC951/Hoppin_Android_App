package com.unh.hoppin_android_app
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unh.hoppin_android_app.Author
import com.unh.hoppin_android_app.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _quickReplies = MutableStateFlow<List<String>>(emptyList())
    val quickReplies = _quickReplies.asStateFlow()

    // A simple map to define the conversation flow based on user input
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
        // Add other branches of the conversation here
    )

    init {
        // Start the conversation
        startConversation()
    }

    private fun startConversation() {
        val initialStep = conversationFlow["START"]
        initialStep?.let { step ->
            viewModelScope.launch {
                for (message in step.botMessages) {
                    addBotMessage(message)
                    delay(800) // Small delay between messages
                }
                _quickReplies.value = step.replies
            }
        }
    }

    fun onUserReply(reply: String) {
        // Add user's reply to the message list
        _messages.value += ChatMessage(reply, Author.USER)
        // Hide quick replies while the bot is "thinking"
        _quickReplies.value = emptyList()

        // Find the next step in the conversation
        val nextStep = conversationFlow[reply]
        viewModelScope.launch {
            delay(1000) // Simulate bot thinking
            if (nextStep != null) {
                for (message in nextStep.botMessages) {
                    addBotMessage(message)
                    delay(800)
                }
                _quickReplies.value = nextStep.replies
            } else {
                // End of conversation or undefined path
                addBotMessage("Sorry, I can't help with that yet!")
            }
        }
    }

    private fun addBotMessage(text: String) {
        _messages.value += ChatMessage(text, Author.BOT)
    }
}

data class ConversationStep(val botMessages: List<String>, val replies: List<String>)