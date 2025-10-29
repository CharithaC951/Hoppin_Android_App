package com.unh.hoppin_android_app.chat


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.unh.hoppin_android_app.viewmodels.LocationViewModel
import com.unh.hoppin_android_app.ui.theme.DarkPurple
import com.unh.hoppin_android_app.ui.theme.LightPurple

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel(),
    locationViewModel: LocationViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val messages by chatViewModel.messages.collectAsState()
    val quickReplies by chatViewModel.quickReplies.collectAsState()
    val locationData by locationViewModel.locationState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat with me") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                backgroundColor = Color.White,
                elevation = 4.dp
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Bottom,
                reverseLayout = true
            ) {
                items(messages.reversed()) { message ->
                    MessageBubble(message = message)
                }
            }

            QuickReplies(
                replies = quickReplies,
                onReplyClicked = { reply ->
                    val coordinates = locationData.coordinates
                    val userLatLng = if (coordinates != null) {
                        LatLng(coordinates.latitude, coordinates.longitude)
                    } else {
                        null
                    }
                    chatViewModel.onUserReply(reply, userLatLng)
                }
            )
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isBot = message.author == Author.BOT
    val horizontalArrangement = if (isBot) Arrangement.Start else Arrangement.End
    val bubbleColor = if (isBot) LightPurple else DarkPurple
    val textColor = if (isBot) Color.Black else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.Top
    ) {
        if (isBot) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = "Bot Avatar",
                tint = Color.Gray,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        Column {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(bubbleColor)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    color = textColor,
                    fontSize = 16.sp
                )
            }

            if (message.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(24.dp),
                    color = DarkPurple,
                    strokeWidth = 2.dp
                )
            }

            if (message.places.isNotEmpty()) {
                PlacesList(places = message.places)
            }
        }
    }
}

@Composable
fun PlacesList(places: List<Place>) {
    LazyRow(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(end = 80.dp)
    ) {
        items(places) { place ->
            PlaceCard(place = place)
        }
    }
}

@Composable
fun PlaceCard(place: Place) {
    Card(
        modifier = Modifier.width(220.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = place.name ?: "Unknown Place",
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = place.address ?: "No address provided",
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.Gray
            )
            place.rating?.let { rating ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = "Rating", tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$rating",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun QuickReplies(replies: List<String>, onReplyClicked: (String) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(replies) { reply ->
            Button(
                onClick = { onReplyClicked(reply) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = LightPurple)
            ) {
                Text(text = reply, color = Color.Black)
            }

        }
    }
}
