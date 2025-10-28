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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unh.hoppin_android_app.Author
import com.unh.hoppin_android_app.ChatMessage
import com.unh.hoppin_android_app.ChatViewModel
import com.unh.hoppin_android_app.ui.theme.DarkPurple
import com.unh.hoppin_android_app.ui.theme.LightPurple
import kotlin.collections.reversed

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val messages by chatViewModel.messages.collectAsState()
    val quickReplies by chatViewModel.quickReplies.collectAsState()

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
        },
        bottomBar = {
            AppBottomNavigation()
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
                reverseLayout = true // Shows the latest message at the bottom
            ) {
                items(messages.reversed()) { message ->
                    MessageBubble(message = message)
                }
            }

            QuickReplies(
                replies = quickReplies,
                onReplyClicked = { reply ->
                    chatViewModel.onUserReply(reply)
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isBot) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = "Bot Avatar",
                tint = Color.Gray,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

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
    }
}

@Composable
fun QuickReplies(
    replies: List<String>,
    onReplyClicked: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        items(replies) { reply ->
            Button(
                onClick = { onReplyClicked(reply) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(backgroundColor = LightPurple),
                elevation = ButtonDefaults.elevation(0.dp)
            ) {
                Text(text = reply, color = Color.Black)
            }
        }
    }
}


@Composable
fun AppBottomNavigation() {
    var selectedItem by remember { mutableStateOf(0) }
    val items = listOf("Home", "Favorites", "Notifications", "Settings")
    val icons = listOf(
        Icons.Default.Home,
        Icons.Default.FavoriteBorder,
        Icons.Default.NotificationsNone,
        Icons.Default.Settings
    )

    BottomNavigation(
        backgroundColor = Color.White,
        contentColor = DarkPurple
    ) {
        items.forEachIndexed { index, screen ->
            BottomNavigationItem(
                icon = { Icon(icons[index], contentDescription = screen) },
                selected = selectedItem == index,
                onClick = { selectedItem = index },
                selectedContentColor = DarkPurple,
                unselectedContentColor = Color.Gray
            )
        }
    }
}