package com.unh.hoppin_android_app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppBottomBar(
    currentRoute: String?,
    onItemClick: (String) -> Unit
) {
    val items = listOf(
        BottomItem(
            route = "Home/{$USER_NAME_ARG}",
            label = "Home",
            icon = Icons.Outlined.Home,
            selectedIcon = Icons.Filled.Home
        ),
        BottomItem(
            route = "favorites",
            label = "Favorites",
            icon = Icons.Outlined.FavoriteBorder,
            selectedIcon = Icons.Filled.FavoriteBorder
        ),
        BottomItem(
            route = "feed",
            label = "Feed",
            icon = Icons.Outlined.DynamicFeed,
            selectedIcon = Icons.Filled.DynamicFeed
        ),
        BottomItem(
            route = "notifications",
            label = "Notifications",
            icon = Icons.Outlined.Notifications,
            selectedIcon = Icons.Filled.Notifications
        ),
        BottomItem(
            route = "settings",
            label = "Settings",
            icon = Icons.Outlined.Settings,
            selectedIcon = Icons.Filled.Settings
        ),
    )

    val orange = Color(color = 0xFFF4b91D)
    val Unselected = Color(0xFF9E9E9E)

    // Outer background (pink strip)
    Box(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp), // EXPANDED HEIGHT
        contentAlignment = Alignment.Center
    ) {
        // Inner pill container (white)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp) // WIDER white pill
                .height(80.dp),      // TALLER bar
            color = Color.White,
            shape = RoundedCornerShape(26.dp), // MORE ROUNDING
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val selected = currentRoute == item.route

                    // Selected highlight (bigger + rounded)
                    val highlightModifier = if (selected) {
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFFF9A9E).copy(alpha = 0.18f)) // prettier highlight
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    } else {
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    }

                    Box(
                        modifier = highlightModifier
                            .weight(1f)
                            .clickable { onItemClick(item.route) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.icon,
                                contentDescription = item.label,
                                tint = if (selected) orange else Unselected,
                                modifier = Modifier.size(26.dp) // slightly bigger
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.label,
                                fontSize = 12.sp,
                                color = if (selected) orange else Unselected,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)
